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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        Mono.fromRunnable(() -> {
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
                        routeAgent, buildRoutePrompt(prompt), sink);
                Mono<AgentResult> itineraryMono = callAgentWithStreaming(
                        itineraryAgent, buildItineraryPrompt(prompt), sink);

                AgentResult[] parallelResults = Mono.zip(routeMono, itineraryMono,
                        (r1, r2) -> new AgentResult[]{r1, r2}).block();

                AgentResult routeResult = parallelResults[0];
                AgentResult itineraryResult = parallelResults[1];

                // Step 2: sequential call Budget
                sink.tryEmitNext(StreamEvent.agentStart(budgetAgent));
                String budgetPrompt = buildBudgetPrompt(prompt, routeResult, itineraryResult);
                AgentResult budgetResult = callAgentWithStreaming(
                        budgetAgent, budgetPrompt, sink).block();

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
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private Mono<AgentResult> callAgentWithStreaming(
            String agentName, String prompt, Sinks.Many<StreamEvent> sink) {

        long startTime = System.currentTimeMillis();

        return Mono.fromCallable(() -> {
            log.info("[{}] 开始调用", agentName);

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

            log.info("[{}] 完成, 耗时{}ms, 结果长度{}", agentName, executionTime, result.length());
            return AgentResult.success(agentName, result, executionTime);

        }).subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.error("[{}] 失败: {}", agentName, e.getMessage());
                    return Mono.just(AgentResult.failure(agentName, e.getMessage(), executionTime));
                });
    }

    private String buildFullPrompt(PlanRequest request) {
        StringBuilder sb = new StringBuilder(request.getPrompt());
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

                请根据用户需求，规划最优的出行路线，包括：
                1. 出发地到目的地的交通方式选择
                2. 具体路线（高速/国道/省道等）
                3. 预计行驶时间和距离
                4. 途经的重要城市或休息点
                5. 油费/过路费等交通成本估算

                请输出详细的路线规划方案。

                【结构化输出要求】
                在方案末尾，请用以下JSON格式输出关键数据：
                ```json
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
                ```
                """, userRequest);
    }

    private String buildItineraryPrompt(String userRequest) {
        return String.format("""
                你是一位专业的旅行行程规划专家。

                用户需求：
                %s

                请根据用户需求，安排详细的每日行程，包括：
                1. 每天的景点安排（上午/下午/晚上）
                2. 各景点的预计游览时间
                3. 餐饮推荐和预计费用
                4. 住宿建议（区域和价位）
                5. 景点门票价格

                请输出详细的行程规划方案。

                【结构化输出要求】
                在方案末尾，请用以下JSON格式输出关键数据：
                ```json
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
                ```
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
                请基于以上信息，进行全面的费用统计和分析：
                1. 费用明细表（交通、住宿、餐饮、门票、其他）
                2. 费用汇总（总预算、人均费用、日均花费）
                3. 三档方案（经济/均衡/舒适）
                4. 优化建议和风险提示

                请输出详细的费用分析报告。

                【结构化输出要求】
                在报告末尾，请用以下JSON格式输出关键数据：
                ```json
                {
                  "total": { "totalBudget": 3000, "perPersonCost": 1500, "dailyAverage": 1000, "travelers": 2 },
                  "breakdown": { "transportation": 800, "accommodation": 1000, "dining": 600, "tickets": 400, "miscellaneous": 200 },
                  "tiers": [
                    { "name": "economy", "totalCost": 2000, "description": "经济方案" }
                  ],
                  "optimizationTips": ["提前预订可节省住宿费"]
                }
                ```
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
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("从(.+?)[到去](.+?)[的游玩]|(.+?)[到去](.+?)[日游]").matcher(request);
        if (matcher.find()) {
            result[0] = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(3).trim();
            result[1] = matcher.group(2) != null ? matcher.group(2).trim() : matcher.group(4).trim();
        }
        return result;
    }
}
