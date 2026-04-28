package managerAgent.tool;

import io.agentscope.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import model.AgentResult;
import utils.AgentUtils;
import utils.ParallelAgentService;

/**
 * author: Sinkendlessly
 * description: 远程Agent调用工具（重构版）
 *      支持并行调用、结果聚合、容错处理
 *      Agent 名称统一从 application.yml agent.names 读取
 * date: 2026
 */
@Slf4j
public class RemoteAgentTool {

    // Agent 名称统一从配置读取，修改时只需改 application.yml
    private static final String ROUTE_AGENT = AgentUtils.getAgentName("route");
    private static final String ITINERARY_AGENT = AgentUtils.getAgentName("itinerary");
    private static final String BUDGET_AGENT = AgentUtils.getAgentName("budget");

    /**
     * 并行调用路线规划和行程规划Agent，然后由费用统计Agent汇总
     *
     * 流程:
     * 1. 并行调用 RouteMakingAgent + TripPlannerAgent
     * 2. 等待两者结果（或超时/失败）
     * 3. 将结果传给 BudgetAgent 进行费用统计
     * 4. 返回完整旅行方案
     *
     * @param userRequest 用户原始请求
     * @return 完整方案文本
     */
    @Tool(description = "并行调用路线规划、行程规划Agent，然后由费用统计Agent汇总分析")
    public String planTravelWithBudget(String userRequest) {
        log.info("========== 开始旅行规划（并行模式） ==========");
        log.info("用户请求: {}", userRequest);

        long totalStartTime = System.currentTimeMillis();

        try {
            // 并行调用路线规划和行程规划Agent，然后调用费用统计Agent
            AgentResult finalResult = ParallelAgentService.callParallelThenSequential(
                    // 第一个Agent：路线规划
                    ROUTE_AGENT,
                    buildRoutePrompt(userRequest),

                    // 第二个Agent：行程规划
                    ITINERARY_AGENT,
                    buildItineraryPrompt(userRequest),

                    // 第三个Agent：费用统计
                    BUDGET_AGENT,

                    // 构建费用统计Agent的提示词（接收前两个Agent的结果）
                    (routeResult, itineraryResult) -> buildBudgetPrompt(userRequest, routeResult, itineraryResult)

            ).block(); // 阻塞等待最终结果

            long totalTime = System.currentTimeMillis() - totalStartTime;

            if (finalResult.isSuccess()) {
                log.info("========== 旅行规划完成，总耗时: {}ms ==========", totalTime);
                return finalResult.getContent();
            } else {
                log.error("========== 旅行规划失败: {} ==========", finalResult.getErrorMessage());
                return String.format("规划失败: %s\n建议: 请稍后重试或简化需求", finalResult.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("旅行规划异常", e);
            return String.format("系统异常: %s", e.getMessage());
        }
    }

    /**
     * 仅调用路线规划Agent（兼容旧模式）
     */
    @Tool(description = "从Nacos注册中心获取路线制定Agent（串行模式）")
    public String callRouteMakingAgent(String userRequest) {
        log.info("调用路线规划Agent");
        AgentResult result = ParallelAgentService.callAgent(
                ROUTE_AGENT,
                buildRoutePrompt(userRequest)
        ).block();

        return result.isSuccess() ? result.getContent() : "路线规划失败: " + result.getErrorMessage();
    }

    /**
     * 仅调用行程规划Agent（兼容旧模式）
     */
    @Tool(description = "从Nacos注册中心获取行程规划Agent（串行模式）")
    public String callTripPlannerAgent(String userRequest) {
        log.info("调用行程规划Agent");
        AgentResult result = ParallelAgentService.callAgent(
                ITINERARY_AGENT,
                buildItineraryPrompt(userRequest)
        ).block();

        return result.isSuccess() ? result.getContent() : "行程规划失败: " + result.getErrorMessage();
    }

    /**
     * 仅调用费用统计Agent（兼容旧模式）
     */
    @Tool(description = "从Nacos注册中心获取费用统计Agent")
    public String callBudgetAgent(String routeInfo, String itineraryInfo) {
        log.info("调用费用统计Agent");
        AgentResult result = ParallelAgentService.callAgent(
                BUDGET_AGENT,
                buildBudgetPrompt("用户旅行规划",
                    AgentResult.success(ROUTE_AGENT, routeInfo, 0),
                    AgentResult.success(ITINERARY_AGENT, itineraryInfo, 0)
                )
        ).block();

        return result.isSuccess() ? result.getContent() : "费用统计失败: " + result.getErrorMessage();
    }

    // ==================== 提示词构建方法 ====================

    /**
     * 构建路线规划Agent的提示词
     */
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

    /**
     * 构建行程规划Agent的提示词
     */
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

    /**
     * 构建费用统计Agent的提示词
     */
    private String buildBudgetPrompt(String userRequest, AgentResult routeResult, AgentResult itineraryResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的旅行费用分析专家。\n\n");
        sb.append("用户原始需求：\n").append(userRequest).append("\n\n");

        // 添加路线规划结果
        if (routeResult.isSuccess()) {
            sb.append("=== 路线规划结果 ===\n")
              .append(routeResult.getContent())
              .append("\n\n");
        } else {
            sb.append("=== 路线规划结果 ===\n")
              .append("【路线规划失败：").append(routeResult.getErrorMessage()).append("】\n")
              .append("请基于行程规划部分进行费用估算\n\n");
        }

        // 添加行程规划结果
        if (itineraryResult.isSuccess()) {
            sb.append("=== 行程规划结果 ===\n")
              .append(itineraryResult.getContent())
              .append("\n\n");
        } else {
            sb.append("=== 行程规划结果 ===\n")
              .append("【行程规划失败：").append(itineraryResult.getErrorMessage()).append("】\n")
              .append("请基于路线规划部分进行费用估算\n\n");
        }

        sb.append("""
                请基于以上信息，进行全面的费用统计和分析：

                1. **费用明细表**
                   - 交通费用（油费、过路费、停车费等）
                   - 住宿费用（按每晚计算）
                   - 餐饮费用（按每天计算）
                   - 门票费用（各景点门票总和）
                   - 其他费用（保险、应急等）

                2. **费用汇总**
                   - 总预算
                   - 人均费用（假设2人出行）
                   - 日均花费

                3. **优化建议**
                   - 哪些费用可以节省
                   - 性价比最高的选择
                   - 预算浮动区间

                4. **风险提示**
                   - 可能的额外支出
                   - 淡旺季价格波动

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
}
