package routeMakingAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import routeMakingAgent.mcp.BaiduMapMCP;
import utils.AgentUtils;
import utils.ToolUtils;

import java.util.Set;

/**
 * author: Sinkendlessly
 * description: 路线制定Agent
 * date: 2026
 */

@Component
public class RouteMakingAgent {

    private static final Logger log = LoggerFactory.getLogger(RouteMakingAgent.class);

    private final BaiduMapMCP baiduMapMCP;

    public RouteMakingAgent(BaiduMapMCP baiduMapMCP) {
        this.baiduMapMCP = baiduMapMCP;
    }

    @Bean
    public ReActAgent getRouteMakingAgent() {

        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                "RouteMakingAgent",
                "擅长处理自驾游路线制定",
                "route-planning"
        );

        baiduMapMCP.getBaiduMapMCP();
        McpClientWrapper mcpClient = baiduMapMCP.initBaiduMapMCP();

        if (mcpClient == null) {
            log.warn("百度地图MCP未启用，RouteMakingAgent将以无地图工具模式启动");
            return builder.build();
        }

        ToolUtils toolUtils = new ToolUtils();
        Toolkit toolkit = toolUtils.getToolkit(mcpClient);

        Set<String> toolNames = toolkit.getToolNames();
        log.info("=============");
        toolNames.forEach(value -> log.info("挂载的工具名称：" + value));
        log.info("=============");

        return builder
                .toolkit(toolkit)
                .build();
    }
}
