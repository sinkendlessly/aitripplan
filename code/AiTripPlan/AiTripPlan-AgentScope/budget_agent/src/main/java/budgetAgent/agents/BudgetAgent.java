package budgetAgent.agents;

import io.agentscope.core.ReActAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import utils.AgentUtils;

/**
 * author: Imooc
 * description: 费用统计Agent
 *      接收路线规划和行程规划的结果，进行费用统计和预算分析
 * date: 2026
 */
@Component
public class BudgetAgent {

    @Bean
    public ReActAgent getBudgetAgent() {

        /* **********************
         *
         * BudgetAgent 职责：
         * 1. 接收路线规划结果（交通费用）
         * 2. 接收行程规划结果（门票、餐饮、住宿费用）
         * 3. 进行费用汇总和预算分析
         * 4. 提供优化建议
         *
         * *********************/

        return AgentUtils.getReActAgentBuilder(
                "BudgetAgent",
                "费用统计Agent，擅长旅行费用预算分析、成本拆解与优化建议",
                "budget-optimization"
        )
                .build();
    }
}
