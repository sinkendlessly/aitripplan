package model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * author: Sinkendlessly
 * description: 旅行规划上下文
 *      用于在多个Agent之间传递数据
 * date: 2026
 */
@Data
@Builder
public class TravelPlanContext {

    /**
     * 用户原始请求
     */
    private String userRequest;

    /**
     * 路线规划结果
     */
    private AgentResult routeResult;

    /**
     * 行程规划结果
     */
    private AgentResult itineraryResult;

    /**
     * 费用统计结果
     */
    private AgentResult budgetResult;

    /**
     * 扩展数据（用于传递额外信息）
     */
    @Builder.Default
    private Map<String, Object> extras = new HashMap<>();

    /**
     * 获取完整结果摘要
     */
    public String getFullSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 用户请求 ===\n").append(userRequest).append("\n\n");

        if (routeResult != null) {
            sb.append("=== 路线规划 ===\n").append(routeResult.getSummary()).append("\n\n");
        }

        if (itineraryResult != null) {
            sb.append("=== 行程规划 ===\n").append(itineraryResult.getSummary()).append("\n\n");
        }

        if (budgetResult != null) {
            sb.append("=== 费用统计 ===\n").append(budgetResult.getSummary()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 检查是否有结果可用
     */
    public boolean hasAnyResult() {
        return (routeResult != null && routeResult.isSuccess())
                || (itineraryResult != null && itineraryResult.isSuccess());
    }

    /**
     * 获取所有成功结果的数量
     */
    public int getSuccessCount() {
        int count = 0;
        if (routeResult != null && routeResult.isSuccess()) count++;
        if (itineraryResult != null && itineraryResult.isSuccess()) count++;
        if (budgetResult != null && budgetResult.isSuccess()) count++;
        return count;
    }
}
