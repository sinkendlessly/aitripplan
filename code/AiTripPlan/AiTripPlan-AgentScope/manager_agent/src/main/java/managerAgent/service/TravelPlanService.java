package managerAgent.service;

import config.AgentProperties;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import utils.AgentDiscoveryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import managerAgent.dto.PlanRequest;
import managerAgent.dto.PlanResponse;
import managerAgent.dto.StreamEvent;
import model.AgentResult;
import model.TravelPlanContext;
import utils.JsonValidator;
import utils.PromptSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import service.TravelPlanCacheService;
import service.TravelPlanHistoryService;
import service.RouteCacheService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旅行规划核心服务（当前主路径 - v2）
 * v2: 代码硬编码编排三个 Agent（并行路线+行程 → 串行预算），可预测、可调试、可控超时
 * v1（实验性备选）: ManagerAgent + ReAct + Tool Calling 驱动，见 ManagerAgent.java
 */
@Slf4j
@Service
public class TravelPlanService {

    @Autowired
    private TravelPlanHistoryService historyService;

    @Autowired
    private TravelPlanCacheService cacheService;

    @Autowired
    private RouteCacheService routeCacheService;

    @Autowired
    private AgentProperties agentProperties;

    private final Map<String, Sinks.Many<StreamEvent>> activeSinks = new ConcurrentHashMap<>();

    /** planId → (response, createdAt) for TTL-based cleanup */
    private final Map<String, PlanEntry> planResults = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "atplan-cleanup");
        t.setDaemon(true);
        return t;
    });

    /** 异步执行规划任务的线程池 */
    private final ExecutorService planExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "atplan-executor");
        t.setDaemon(true);
        return t;
    });

    private static final long PLAN_TTL_MINUTES = 30;
    private static final long SINK_CLEANUP_DELAY_SECONDS = 120;

    record PlanEntry(PlanResponse response, long createdAt) {}

    @PostConstruct
    void startCleanup() {
        cleanupScheduler.scheduleAtFixedRate(this::evictExpiredPlans, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    void shutdown() {
        cleanupScheduler.shutdownNow();
        planExecutor.shutdownNow();
    }

    private void evictExpiredPlans() {
        long now = System.currentTimeMillis();
        long ttl = TimeUnit.MINUTES.toMillis(PLAN_TTL_MINUTES);
        int before = planResults.size();
        planResults.values().removeIf(entry -> (now - entry.createdAt()) > ttl);
        int removed = before - planResults.size();
        if (removed > 0) {
            log.info("[清理] 移除了 {} 个过期规划结果", removed);
        }
    }

    public PlanResponse createPlan(PlanRequest request) {
        String planId = generatePlanId();
        String sessionId = cacheService.createSession();

        historyService.createRecord(sessionId, request.getPrompt());

        PlanResponse response = PlanResponse.builder()
                .planId(planId)
                .sessionId(sessionId)
                .status("PROCESSING")
                .streamUrl("/api/v1/plan/" + planId + "/stream")
                .createdAt(LocalDateTime.now())
                .userRequest(request.getPrompt())
                .build();

        planResults.put(planId, new PlanEntry(response, System.currentTimeMillis()));

        Sinks.Many<StreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer(256);
        activeSinks.put(planId, sink);

        executePlanAsync(planId, sessionId, request, sink);

        return response;
    }

    public Flux<StreamEvent> getStream(String planId) {
        Sinks.Many<StreamEvent> sink = activeSinks.get(planId);
        if (sink == null) {
            return Flux.just(StreamEvent.error("规划任务不存在或已结束"));
        }
        return sink.asFlux()
                .timeout(Duration.ofSeconds(120))
                .onErrorResume(e -> Flux.just(StreamEvent.error("连接超时")));
    }

    public PlanResponse getPlan(String planId) {
        PlanEntry entry = planResults.get(planId);
        if (entry != null && !"PROCESSING".equals(entry.response().getStatus())) {
            return entry.response();
        }
        return entry != null ? entry.response() : null;
    }

    private void executePlanAsync(String planId, String sessionId,
                                  PlanRequest request, Sinks.Many<StreamEvent> sink) {
        planExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                sink.tryEmitNext(StreamEvent.thinking("正在分析您的旅行需求..."));

                String prompt = buildFullPrompt(request);

                String routeAgent = agentProperties.getAgentName("route");
                String itineraryAgent = agentProperties.getAgentName("itinerary");
                String budgetAgent = agentProperties.getAgentName("budget");

                // Step 1: parallel call Route + Itinerary
                sink.tryEmitNext(StreamEvent.agentStart(routeAgent));
                sink.tryEmitNext(StreamEvent.agentStart(itineraryAgent));

                Mono<AgentResult> routeMono = callAgentWithStreaming(
                        routeAgent, buildRoutePrompt(prompt), sink, planId);
                Mono<AgentResult> itineraryMono = callAgentWithStreaming(
                        itineraryAgent, buildItineraryPrompt(prompt), sink, planId);

                AgentResult[] parallelResults = Mono.zip(routeMono, itineraryMono,
                        (r1, r2) -> new AgentResult[]{r1, r2}).block();

                AgentResult routeResult = parallelResults[0];
                AgentResult itineraryResult = parallelResults[1];

                // 校验JSON格式：校验不通过只记日志不阻断，前端会展示原始文本兜底
                if (routeResult.isSuccess() && !JsonValidator.isValidRoute(routeResult.getContent())) {
                    log.warn("[{}] 路线JSON校验不通过，将使用原始文本展示", routeAgent);
                }
                if (itineraryResult.isSuccess() && !JsonValidator.isValidItinerary(itineraryResult.getContent())) {
                    log.warn("[{}] 行程JSON校验不通过，将使用原始文本展示", itineraryAgent);
                }

                // Step 2: sequential call Budget
                sink.tryEmitNext(StreamEvent.agentStart(budgetAgent));
                String budgetPrompt = buildBudgetPrompt(prompt, routeResult, itineraryResult);
                AgentResult budgetResult = callAgentWithStreaming(
                        budgetAgent, budgetPrompt, sink, planId).block();

                // 校验预算JSON格式
                if (budgetResult.isSuccess() && !JsonValidator.isValidBudget(budgetResult.getContent())) {
                    log.warn("[{}] 预算JSON校验不通过，将使用原始文本展示", budgetAgent);
                }

                long totalTime = System.currentTimeMillis() - startTime;

                // Build context and persist
                TravelPlanContext context = TravelPlanContext.builder()
                        .userRequest(prompt)
                        .routeResult(routeResult)
                        .itineraryResult(itineraryResult)
                        .budgetResult(budgetResult)
                        .build();

                historyService.saveAsync(sessionId, context, totalTime);

                // Cache route result if successful
                if (routeResult.isSuccess()) {
                    String[] od = parseOriginDestination(request.getPrompt());
                    if (od[0] != null && od[1] != null) {
                        routeCacheService.cacheRoute(od[0], od[1], routeResult);
                    }
                }

                // Update plan response
                PlanResponse result = PlanResponse.builder()
                        .planId(planId)
                        .sessionId(sessionId)
                        .status(determineStatus(context))
                        .createdAt(planResults.get(planId).response().getCreatedAt())
                        .userRequest(request.getPrompt())
                        .routeResult(routeResult.isSuccess() ? routeResult.getContent() : null)
                        .itineraryResult(itineraryResult.isSuccess() ? itineraryResult.getContent() : null)
                        .budgetResult(budgetResult.isSuccess() ? budgetResult.getContent() : null)
                        .executionTime(totalTime)
                        .build();
                planResults.put(planId, new PlanEntry(result, System.currentTimeMillis()));

                sink.tryEmitNext(StreamEvent.complete(planId, totalTime));

            } catch (Exception e) {
                log.error("[规划异常] planId={}", planId, e);
                long totalTime = System.currentTimeMillis() - startTime;

                PlanResponse errorResult = PlanResponse.builder()
                        .planId(planId)
                        .sessionId(sessionId)
                        .status("FAILED")
                        .errorMessage(e.getMessage())
                        .executionTime(totalTime)
                        .build();
                planResults.put(planId, new PlanEntry(errorResult, System.currentTimeMillis()));

                historyService.markFailed(sessionId, e.getMessage());
                sink.tryEmitNext(StreamEvent.error(e.getMessage()));
            } finally {
                sink.tryEmitComplete();
                // Delay cleanup to allow reconnection, using dedicated scheduler
                cleanupScheduler.schedule(() -> {
                    activeSinks.remove(planId);
                    log.debug("[清理] 已移除 planId={} 的 Sink", planId);
                }, SINK_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        });
    }

    private Mono<AgentResult> callAgentWithStreaming(
            String agentName, String prompt, Sinks.Many<StreamEvent> sink, String planId) {

        long startTime = System.currentTimeMillis();

        return Mono.fromCallable(() -> {
            log.info("[{}][planId={}] 开始调用", agentName, planId);

            A2aAgent agent = A2aAgent.builder()
                    .name(agentName)
                    .agentCardResolver(AgentDiscoveryService.getSharedResolver())
                    .build();

            Msg message = Msg.builder()
                    .role(MsgRole.USER)
                    .content(java.util.List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            StringBuilder resultBuilder = new StringBuilder();
            agent.stream(message)
                    .doOnNext(event -> {
                        if (event.getMessage() != null) {
                            String text = event.getMessage().getTextContent();
                            resultBuilder.append(text);
                            if (text != null && !text.isEmpty()) {
                                sink.tryEmitNext(StreamEvent.progress(agentName, text));
                            }
                        }
                    })
                    .blockLast();

            long executionTime = System.currentTimeMillis() - startTime;
            String result = resultBuilder.toString();

            log.info("[{}][planId={}] 完成, 耗时{}ms, 结果长度{}", agentName, planId, executionTime, result.length());
            return AgentResult.success(agentName, result, executionTime);

        }).subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.error("[{}][planId={}] 失败: {}", agentName, planId, e.getMessage());
                    return Mono.just(AgentResult.failure(agentName, e.getMessage(), executionTime));
                });
    }

    private String buildFullPrompt(PlanRequest request) {
        String sanitized = PromptSanitizer.sanitize(request.getPrompt());
        StringBuilder sb = new StringBuilder(sanitized);
        if (request.getOptions() != null) {
            PlanRequest.PlanOptions opts = request.getOptions();
            if (opts.getBudget() != null) {
                sb.append("\n预算：").append(opts.getBudget()).append("元");
            }
            if (opts.getTravelers() != null) {
                sb.append("\n出行人数：").append(opts.getTravelers()).append("人");
            }
            if (opts.getTravelMode() != null) {
                sb.append("\n出行方式：").append(opts.getTravelMode());
            }
            if (opts.getPreferences() != null && !opts.getPreferences().isEmpty()) {
                sb.append("\n偏好：").append(String.join("、", opts.getPreferences()));
            }
        }
        return sb.toString();
    }

    private String buildRoutePrompt(String userRequest) {
        return String.format("""
                你是一位专业的路线规划专家。

                用户需求：
                %s

                请规划最优出行路线，包括交通方式、路线分段、距离和时间。

                【输出要求】
                请严格按照以下JSON格式输出，不要输出任何其他文字和解释，不要使用```json代码块：
                {
                  "origin": "出发地",
                  "destination": "目的地",
                  "totalDistanceKm": 120,
                  "estimatedDurationMin": 150,
                  "transportMode": "自驾",
                  "segments": [
                    { "from": "A", "to": "B", "roadName": "G25高速", "distanceKm": 80, "durationMin": 60 }
                  ]
                }
                """, userRequest);
    }

    private String buildItineraryPrompt(String userRequest) {
        return String.format("""
                你是一位专业的旅行行程规划专家。

                用户需求：
                %s

                请安排详细的每日行程，包括景点、餐饮和住宿建议。

                【输出要求】
                请严格按照以下JSON格式输出，不要输出任何其他文字和解释，不要使用```json代码块：
                {
                  "totalDays": 3,
                  "days": [
                    {
                      "day": 1,
                      "morning": [{ "name": "景点名", "type": "scenic_spot", "durationMin": 120, "estimatedCost": 60 }],
                      "afternoon": [],
                      "evening": [],
                      "accommodation": "推荐住宿区域"
                    }
                  ]
                }
                """, userRequest);
    }

    private String buildBudgetPrompt(String userRequest,
                                     AgentResult routeResult, AgentResult itineraryResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的旅行费用分析专家。\n\n");
        sb.append("用户原始需求：\n").append(userRequest).append("\n\n");

        if (routeResult.isSuccess()) {
            sb.append("=== 路线规划结果 ===\n").append(routeResult.getContent()).append("\n\n");
        } else {
            sb.append("=== 路线规划结果 ===\n【失败：")
              .append(routeResult.getErrorMessage()).append("】\n\n");
        }

        if (itineraryResult.isSuccess()) {
            sb.append("=== 行程规划结果 ===\n").append(itineraryResult.getContent()).append("\n\n");
        } else {
            sb.append("=== 行程规划结果 ===\n【失败：")
              .append(itineraryResult.getErrorMessage()).append("】\n\n");
        }

        sb.append("""
                请基于以上信息，进行全面的费用统计和分析：费用明细、费用汇总、三档方案、优化建议。

                【输出要求】
                请严格按照以下JSON格式输出，不要输出任何其他文字和解释，不要使用```json代码块：
                {
                  "total": { "totalBudget": 3000, "perPersonCost": 1500, "dailyAverage": 1000, "travelers": 2 },
                  "breakdown": { "transportation": 800, "accommodation": 1000, "dining": 600, "tickets": 400, "miscellaneous": 200 },
                  "tiers": [
                    { "name": "economy", "totalCost": 2000, "description": "经济方案" }
                  ],
                  "optimizationTips": ["提前预订可节省住宿费"]
                }
                """);

        return sb.toString();
    }

    private String determineStatus(TravelPlanContext context) {
        int success = context.getSuccessCount();
        if (success == 3) return "SUCCESS";
        if (success > 0) return "PARTIAL";
        return "FAILED";
    }

    private String generatePlanId() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String random = Long.toHexString(System.nanoTime()).substring(0, 6);
        return "plan_" + date + "_" + random;
    }

    private String[] parseOriginDestination(String request) {
        String[] result = new String[]{null, null};

        // 精确匹配：从A到B、A去B
        Pattern precise = Pattern.compile("从(.+?)(?:到|去)(.+?)(?:[的游玩]|$)|([^到去]+?)(?:到|去)(.+?)(?:[的游玩]|$)");
        Matcher matcher = precise.matcher(request);
        if (matcher.find()) {
            result[0] = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(3).trim();
            result[1] = matcher.group(2) != null ? matcher.group(2).trim() : matcher.group(4).trim();
            // 去掉末尾的可能标点
            if (result[0] != null) result[0] = result[0].replaceAll("[，,。.]", "");
            if (result[1] != null) result[1] = result[1].replaceAll("[，,。.]", "");
        }

        // Fallback: "我想去北京"、"计划去上海" 这种没有明确"从A到B"结构的
        if (result[0] == null || result[1] == null) {
            Pattern fallback = Pattern.compile("(?:去|到|在)(\\S{2,4}(?:市|区|县|镇))");
            Matcher fm = fallback.matcher(request);
            String first = fm.find() ? fm.group(1) : null;
            String second = fm.find() ? fm.group(1) : null;
            if (first != null && second != null) {
                result[0] = first;
                result[1] = second;
            } else if (first != null) {
                // 只有一个地点，认为是目的地
                result[1] = first;
            }
        }

        return result;
    }
}
