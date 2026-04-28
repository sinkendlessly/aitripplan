package managerAgent.hook;

import io.agentscope.core.hook.*;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.plan.model.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 计划拦截器，用于监控 Agent 的推理和工具调用过程
 */
public class PlanHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(PlanHook.class);

    private final PlanNotebook plan;

    public PlanHook(PlanNotebook planNotebook) {
        this.plan = planNotebook;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {

        if (event instanceof PreReasoningEvent e) {
            String reason = e.getInputMessages().get(0).getTextContent();
            log.info("[Hook] 用户Prompt: {}", reason);
        }
        else if (event instanceof PostReasoningEvent e) {
            String reason = e.getReasoningMessage().getTextContent();
            log.info("[Hook] 思考过程: {}", reason);

            Plan currentPlan = plan.getCurrentPlan();
            if (currentPlan != null) {
                log.info("[Hook] 计划已生成: {}", currentPlan);
            }
        }
        else if (event instanceof PostActingEvent e) {
            String toolName = e.getToolUse().getName();
            log.info("[Hook] 调用工具: {}", toolName);
        }

        return Mono.just(event);
    }
}
