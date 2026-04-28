package managerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import managerAgent.hook.PlanHook;
import managerAgent.plan.TripPlan;
import managerAgent.tool.RemoteAgentTool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import utils.AgentUtils;
import utils.ToolUtils;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
public class ManagerAgent {

    private ReActAgent agent;

    @PostConstruct
    public void init() {
        TripPlan plan = new TripPlan();
        PlanNotebook planNotebook = plan.getPlan();

        ToolUtils toolUtils = new ToolUtils();
        Toolkit toolkit = toolUtils.getToolkit(new RemoteAgentTool());

        agent = AgentUtils.getReActAgentBuilder(
                "ManagerAgent",
                """
                你是ATPlan旅行规划系统的主管Agent，负责统筹协调多个专业Agent完成用户的旅行规划需求。

                你的核心能力：
                1. 理解用户的旅行需求（目的地、天数、预算、偏好等）
                2. 调用【planTravelWithBudget】工具进行完整的旅行规划
                   - 该工具会并行调用路线规划、行程规划Agent
                   - 然后由费用统计Agent进行汇总分析
                   - 最终返回包含路线、行程、预算的完整方案
                3. 对返回的方案进行整合和优化建议

                工作流程：
                1. 接收用户需求
                2. 调用 planTravelWithBudget 工具
                3. 将工具返回的完整方案呈现给用户
                4. 根据用户反馈进行迭代优化
                """,
                "route-planning",
                "itinerary-planning",
                "budget-optimization",
                "travel-planning-closed-loop"
        )
                .planNotebook(planNotebook)
                .hook(new PlanHook(planNotebook))
                .toolkit(toolkit)
                .build();

        log.info("ManagerAgent 初始化完成");
    }

    public Flux<Event> stream(String prompt) {
        log.info("ManagerAgent 开始执行, prompt长度: {}", prompt.length());
        return AgentUtils.streamResponse(agent, prompt);
    }
}
