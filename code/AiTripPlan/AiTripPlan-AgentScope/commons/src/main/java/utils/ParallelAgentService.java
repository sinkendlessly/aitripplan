package utils;

import config.AgentProperties;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import model.AgentResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * author: Imooc
 * description: 并行Agent调用服务（配置化版本）
 *      支持并行调用多个Agent，统一处理超时和容错
 *      支持熔断降级（Resilience4j）与重试
 *      支持分布式追踪（Micrometer Observation）
 * date: 2026
 */
@Slf4j
@Component
public class ParallelAgentService {

    private static AgentProperties getProps() {
        return SpringContextHolder.getBean(AgentProperties.class);
    }

    // ==================== 公开入口 ====================

    /**
     * 调用单个Agent（使用默认超时）
     */
    public static Mono<AgentResult> callAgent(String agentName, String prompt) {
        return callAgent(agentName, prompt, getProps().getTimeoutSeconds());
    }

    /**
     * 调用单个Agent（带超时、重试、Traced）
     */
    public static Mono<AgentResult> callAgent(String agentName, String prompt, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        ObservationRegistry registry = tryGetObservationRegistry();

        // 1) 熔断器检查——OPEN 状态快速拒绝
        if (!checkCircuitBreaker(agentName)) {
            return Mono.just(AgentResult.failure(agentName,
                    "服务熔断：Agent[" + agentName + "] 暂不可用，请稍后重试", 0));
        }

        // 2) 执行 Agent 调用（含追踪 + 重试）
        return Mono.defer(() -> {
                    Observation observation = Observation.createNotStarted("agent.call." + agentName, registry)
                            .lowCardinalityKeyValue("agent", agentName);
                    return observation.observe(() ->
                            doCallAgent(agentName, prompt, timeoutSeconds, startTime)
                    );
                })
                .transformDeferred(applyRetry(agentName))
                .doOnNext(result -> recordCircuitBreakerResult(agentName, result))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 并行调用两个Agent
     */
    public static Mono<AgentResult[]> callTwoAgentsParallel(
            String agent1Name, String agent1Prompt,
            String agent2Name, String agent2Prompt) {

        log.info("开始并行调用两个Agent: [{}] 和 [{}]", agent1Name, agent2Name);

        Mono<AgentResult> mono1 = callAgent(agent1Name, agent1Prompt);
        Mono<AgentResult> mono2 = callAgent(agent2Name, agent2Prompt);

        return Mono.zip(mono1, mono2, (r1, r2) -> new AgentResult[]{r1, r2});
    }

    /**
     * 并行调用两个Agent后，再调用第三个Agent处理结果
     * 流程: (Agent1 || Agent2) -> Agent3
     */
    public static Mono<AgentResult> callParallelThenSequential(
            String firstAgentName, String firstPrompt,
            String secondAgentName, String secondPrompt,
            String thirdAgentName,
            java.util.function.BiFunction<AgentResult, AgentResult, String> thirdPromptBuilder) {

        return callTwoAgentsParallel(
                firstAgentName, firstPrompt,
                secondAgentName, secondPrompt
        ).flatMap(results -> {
            AgentResult result1 = results[0];
            AgentResult result2 = results[1];

            if (!result1.isSuccess() && !result2.isSuccess()) {
                log.error("两个前置Agent都失败: [{}] - {}, [{}] - {}",
                        firstAgentName, result1.getErrorMessage(),
                        secondAgentName, result2.getErrorMessage());
                return Mono.just(AgentResult.failure(
                        thirdAgentName,
                        "前置服务全部失败，无法继续",
                        result1.getExecutionTime() + result2.getExecutionTime()
                ));
            }

            String thirdPrompt = thirdPromptBuilder.apply(result1, result2);
            log.info("前置Agent完成，开始调用 [{}] 进行汇总分析", thirdAgentName);
            return callAgent(thirdAgentName, thirdPrompt);
        });
    }

    /**
     * 快速失败模式
     */
    public static Mono<AgentResult[]> callTwoAgentsParallelFastFail(
            String agent1Name, String agent1Prompt,
            String agent2Name, String agent2Prompt) {

        return callTwoAgentsParallel(agent1Name, agent1Prompt, agent2Name, agent2Prompt)
                .flatMap(results -> {
                    for (AgentResult result : results) {
                        if (!result.isSuccess()) {
                            return Mono.error(new RuntimeException(
                                    String.format("Agent [%s] 失败: %s",
                                            result.getAgentName(), result.getErrorMessage())
                            ));
                        }
                    }
                    return Mono.just(results);
                });
    }

    // ==================== 内部实现 ====================

    private static Mono<AgentResult> doCallAgent(String agentName, String prompt,
                                                  int timeoutSeconds, long startTime) {
        return Mono.fromCallable(() -> {
                    log.info("[{}] 开始调用，提示词长度: {}", agentName, prompt.length());

                    A2aAgent agent = A2aAgent.builder()
                            .name(agentName)
                            .agentCardResolver(AgentDiscoveryService.getSharedResolver())
                            .build();

                    Msg message = Msg.builder()
                            .role(MsgRole.USER)
                            .content(List.of(TextBlock.builder().text(prompt).build()))
                            .build();

                    StringBuilder resultBuilder = new StringBuilder();
                    agent.stream(message)
                            .doOnNext(event -> {
                                if (event.getMessage() != null) {
                                    resultBuilder.append(event.getMessage().getTextContent());
                                }
                            })
                            .blockLast();

                    long executionTime = System.currentTimeMillis() - startTime;
                    String result = resultBuilder.toString();

                    log.info("[{}] 调用成功，耗时: {}ms，返回长度: {}",
                            agentName, executionTime, result.length());

                    return AgentResult.success(agentName, result, executionTime);
                })
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume(throwable -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    String errorMsg;

                    if (throwable instanceof TimeoutException) {
                        errorMsg = String.format("调用超时（%d秒）", timeoutSeconds);
                        log.error("[{}] {}", agentName, errorMsg);
                    } else {
                        errorMsg = throwable.getMessage();
                        log.error("[{}] 调用失败: {}", agentName, errorMsg, throwable);
                    }

                    return Mono.just(AgentResult.failure(agentName, errorMsg, executionTime));
                });
    }

    /**
     * 熔断器检查（使用 SimpleCircuitBreaker）
     * 在调用 Agent 前检查熔断状态，避免发送请求到不可用的服务
     */
    private static boolean checkCircuitBreaker(String agentName) {
        SimpleCircuitBreaker cb = SimpleCircuitBreaker.getOrCreate("agent-" + agentName);
        if (!cb.isCallAllowed()) {
            log.warn("[{}] 熔断器 OPEN，快速拒绝请求", agentName);
            return false;
        }
        return true;
    }

    /**
     * 记录 Agent 调用结果到熔断器
     */
    private static void recordCircuitBreakerResult(String agentName, AgentResult result) {
        SimpleCircuitBreaker cb = SimpleCircuitBreaker.getOrCreate("agent-" + agentName);
        if (result.isSuccess()) {
            cb.onSuccess();
        } else {
            cb.onFailure();
        }
    }

    /**
     * 重试策略（超时类异常不重试，仅重试连接类异常）
     */
    private static <T> Function<Mono<T>, Mono<T>> applyRetry(String agentName) {
        return mono -> mono.retryWhen(
                Retry.backoff(1, Duration.ofSeconds(2))
                        .filter(th -> !(th instanceof TimeoutException))
                        .onRetryExhaustedThrow((spec, retrySignal) -> retrySignal.failure())
                        .doBeforeRetry(rs -> log.warn("[{}] 重试中... (attempt {})",
                                agentName, rs.totalRetries() + 1))
        );
    }

    /**
     * 获取 ObservationRegistry（可能未配置 Micrometer）
     */
    private static ObservationRegistry tryGetObservationRegistry() {
        try {
            return SpringContextHolder.getBean(ObservationRegistry.class);
        } catch (Exception e) {
            return ObservationRegistry.NOOP;
        }
    }
}
