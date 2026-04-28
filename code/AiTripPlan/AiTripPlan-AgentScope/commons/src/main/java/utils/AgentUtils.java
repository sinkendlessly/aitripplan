package utils;

import config.AgentProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * author: Sinkendlessly
 * description: ReActAgent 工具类（配置化版本）
 *      支持从application.yml加载配置
 * date: 2026
 */
@Slf4j
@Component
public class AgentUtils {

    /**
     * 获取配置属性（通过 SpringContextHolder 避免静态字段注入）
     */
    public static AgentProperties getProperties() {
        return SpringContextHolder.getBean(AgentProperties.class);
    }

    /**
     * author: Sinkendlessly
     * description: 创建ReAct Agent Builder
     *
     * @param name: Agent名称
     * @param description: Agent描述
     * @return io.agentscope.core.ReActAgent.Builder
     */
    public static ReActAgent.Builder getReActAgentBuilder(
            String name,
            String description
    ) {
        return getReActAgentBuilder(name, description, new String[0]);
    }

    /**
     * author: Sinkendlessly
     * description: 创建带Skills规范注入的ReAct Agent Builder（配置化版本）
     *
     * @param name: Agent名称
     * @param description: Agent描述
     * @param skillNames: skills目录下的技能名称（如 route-planning）
     * @return io.agentscope.core.ReActAgent.Builder
     */
    public static ReActAgent.Builder getReActAgentBuilder(
            String name,
            String description,
            String... skillNames
    ) {
        AgentProperties props = getProperties();

        // 使用SkillPromptLoader丰富描述
        String enrichedDescription = SkillPromptLoader.enrichDescription(description, skillNames);

        log.debug("创建Agent: {}, 模型: {}, Skills: {}",
                name, props.getModelName(), skillNames);

        return ReActAgent.builder()
                .name(name)
                .description(enrichedDescription)
                .model(DashScopeChatModel.builder()
                        // 从配置读取API Key
                        .apiKey(props.getApiKey())
                        // 从配置读取模型名称
                        .modelName(props.getModelName())
                        .stream(props.isStream())
                        .build())
                ;
    }

    /**
     * author: Sinkendlessly
     * description: ReAct Agent 流式响应
     *
     * @param agent: Agent实例
     * @param prompt: 用户提示词
     * @return reactor.core.publisher.Flux<io.agentscope.core.agent.Event>
     */
    public static Flux<Event> streamResponse(
            AgentBase agent,
            String prompt) {

        return agent.stream(
                // Prompt
                Msg.builder()
                        // 消息角色
                        .role(MsgRole.USER)
                        // 消息内容 (Prompt)
                        .content(List.of(
                                TextBlock.builder()
                                        .text(prompt)
                                        .build()
                        ))
                        .build()
        );
    }

    /**
     * 获取Agent名称（从配置映射）
     */
    public static String getAgentName(String key) {
        return getProperties().getAgentName(key);
    }

    /**
     * 获取超时时间（秒）
     */
    public static int getTimeoutSeconds() {
        return getProperties().getTimeoutSeconds();
    }
}
