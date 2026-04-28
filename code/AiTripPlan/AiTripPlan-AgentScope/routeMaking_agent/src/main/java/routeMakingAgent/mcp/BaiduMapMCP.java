package routeMakingAgent.mcp;

import config.AgentProperties;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Optional;

/**
 * author: Imooc
 * description: 百度地图 MCP Client（配置化版本）
 *      从application.yml加载MCP配置
 * date: 2026
 */
@Slf4j
@Component
public class BaiduMapMCP {

    @Autowired
    private AgentProperties agentProperties;

    // MCP 客户端
    private McpClientWrapper baiduMapMCP = null;
    // MCP 客户端初始化
    private boolean mcpInitialized = false;

    @PostConstruct
    public void init() {
        log.info("BaiduMapMCP初始化，MCP地址: {}",
                agentProperties.getMcp().getBaiduMap().getUrl());
    }

    /**
     * author: Imooc
     * description: 创建百度地图MCP客户端
     *
     * @return void
     */
    public void getBaiduMapMCP() {
        AgentProperties.McpProperties.BaiduMapProperties baiduMapProps =
                agentProperties.getMcp().getBaiduMap();

        String mcpUrl = baiduMapProps.getUrl();
        int timeout = baiduMapProps.getTimeout();

        if (mcpUrl == null || mcpUrl.isBlank() || mcpUrl.contains("your-mcp-server-url")) {
            log.warn("百度地图MCP地址未配置，请设置 BAIDU_MCP_URL 环境变量");
            baiduMapMCP = null;
            mcpInitialized = false;
            return;
        }

        log.info("正在创建百度地图MCP客户端，地址: {}，超时: {}秒", mcpUrl.trim(), timeout);

        try {
            baiduMapMCP = McpClientBuilder.create("BaiduMap-mcp")
                    // 和MCP Server以SSE方式进行通信
                    .sseTransport(mcpUrl.trim())
                    // 请求超时
                    .timeout(Duration.ofSeconds(timeout))
                    // 异步请求
                    .buildAsync()
                    .block();

            log.info("百度地图MCP客户端创建完成");
        } catch (Exception e) {
            log.warn("百度地图MCP客户端创建失败，RouteMakingAgent将不挂载地图工具: {}", e.getMessage());
            baiduMapMCP = null;
            mcpInitialized = false;
        }
    }

    /**
     * author: Imooc
     * description: 初始化百度地图MCP客户端
     *
     * @return io.agentscope.core.tool.mcp.McpClientWrapper
     */
    public McpClientWrapper initBaiduMapMCP() {

        // 通过Optional判断百度MCP客户端是否为null
        Optional<McpClientWrapper> mcpClientWrapper = Optional.ofNullable(baiduMapMCP);
        if (mcpClientWrapper.isPresent()) {
            log.info("==================");
            log.info("百度MCP客户端已经创建");
            log.info("==================");

            if (!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {
                        try {
                            // MCP客户端初始化
                            baiduMapMCP.initialize().block();

                            // 获取MCP服务端工具列表
                            if (baiduMapMCP.isInitialized()) {

                                log.info("=============");
                                log.info("百度地图MCP 客户端初始化成功！");
                                log.info("=============");

                                baiduMapMCP.listTools().block().forEach(tool -> {
                                    log.info("==================");
                                    log.info("百度地图MCP工具列表：" + tool.name());
                                    log.info("==================");
                                });

                                mcpInitialized = true;
                            }
                        } catch (Exception e) {
                            log.warn("百度地图MCP客户端初始化失败，RouteMakingAgent将不挂载地图工具: {}", e.getMessage());
                            baiduMapMCP = null;
                            mcpInitialized = false;
                        }
                    }
                }
            }

        } else {
            log.warn("百度地图MCP客户端未创建，请检查配置");
        }

        return baiduMapMCP;

    }

    /**
     * 检查MCP是否已初始化
     */
    public boolean isInitialized() {
        return mcpInitialized;
    }

    /**
     * 获取MCP客户端
     */
    public McpClientWrapper getClient() {
        return baiduMapMCP;
    }
}
