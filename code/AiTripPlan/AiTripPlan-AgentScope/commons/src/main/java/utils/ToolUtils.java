package utils;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * author: Sinkendlessly
 * description: Agent Tool 工具类
 * date: 2026
 */

@Slf4j
public class ToolUtils {

    private final Toolkit toolkit ;

    public ToolUtils() {
        //创建工具包
        toolkit = new Toolkit();
    }

    /**
     * author: Sinkendlessly
     * description: 获取工具包
     * @param tool:
     * @return io.agentscope.core.tool.Toolkit
     */
    public Toolkit getToolkit(Object tool) {

        //把工具添加到工具包，能自动扫描@Tool所注释的方法，作为Agent的工具
        toolkit.registerTool(tool);

        return toolkit;
    }

    /**
     * author: Sinkendlessly
     * description: 获取工具包
     * @param mcp:
     * @return io.agentscope.core.tool.Toolkit
     */
    public Toolkit getToolkit(McpClientWrapper mcp) {

        if (mcp == null) {
            log.warn("MCP客户端为空，跳过MCP工具注册");
            return toolkit;
        }

        try {
            //把MCP服务端的所有工具添加到工具包
            toolkit.registerMcpClient(mcp).block();
        } catch (Exception e) {
            log.warn("MCP工具注册失败，继续以无MCP工具模式启动: {}", e.getMessage());
        }

        return toolkit;
    }
}
