package model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预算规划结构化输出契约
 * BudgetAgent 的输出应尽量遵循此结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPlan {
    private TotalCost total;
    private CostBreakdown breakdown;
    private List<PlanTier> tiers;
    private List<String> optimizationTips;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalCost {
        private Integer totalBudget;
        private Integer perPersonCost;
        private Integer dailyAverage;
        private Integer travelers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostBreakdown {
        private Integer transportation;
        private Integer accommodation;
        private Integer dining;
        private Integer tickets;
        private Integer miscellaneous;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanTier {
        private String name;        // economy / balanced / comfortable
        private Integer totalCost;
        private String description;
    }
}
