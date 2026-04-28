package model;

import lombok.Builder;
import lombok.Data;

/**
 * author: Imooc
 * description: Agent调用结果封装
 * date: 2026
 */
@Data
@Builder
public class AgentResult {

    /**
     * Agent名称
     */
    private String agentName;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 返回内容
     */
    private String content;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    private long executionTime;

    /**
     * 创建成功结果
     */
    public static AgentResult success(String agentName, String content, long executionTime) {
        return AgentResult.builder()
                .agentName(agentName)
                .success(true)
                .content(content)
                .executionTime(executionTime)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static AgentResult failure(String agentName, String errorMessage, long executionTime) {
        return AgentResult.builder()
                .agentName(agentName)
                .success(false)
                .errorMessage(errorMessage)
                .executionTime(executionTime)
                .build();
    }

    /**
     * 获取结果摘要（用于日志和提示词拼接）
     */
    public String getSummary() {
        if (success) {
            return String.format("【%s】执行成功，耗时%dms\n%s",
                    agentName, executionTime, content);
        } else {
            return String.format("【%s】执行失败，耗时%dms，错误：%s",
                    agentName, executionTime, errorMessage);
        }
    }

    // ========== 结构化解析（最佳努力） ==========

    /**
     * 尝试将输出解析为 RoutePlan（多策略：代码块 → JSON对象 → JSON数组 → 启发式）
     */
    public RoutePlan parseRoute() {
        if (content == null) return null;
        return utils.AgentResultParser.tryExtract(content, RoutePlan.class);
    }

    /**
     * 尝试将输出解析为 ItineraryPlan（多策略：代码块 → JSON对象 → JSON数组 → 启发式）
     */
    public ItineraryPlan parseItinerary() {
        if (content == null) return null;
        return utils.AgentResultParser.tryExtract(content, ItineraryPlan.class);
    }

    /**
     * 尝试将输出解析为 BudgetPlan（多策略：代码块 → JSON对象 → JSON数组 → 启发式）
     */
    public BudgetPlan parseBudget() {
        if (content == null) return null;
        return utils.AgentResultParser.tryExtract(content, BudgetPlan.class);
    }
}
