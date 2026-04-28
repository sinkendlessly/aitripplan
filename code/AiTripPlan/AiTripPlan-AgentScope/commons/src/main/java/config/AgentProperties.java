package config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * author: Imooc
 * description: Agent配置属性类
 *      从application.yml加载配置
 * date: 2026
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /**
     * DashScope API Key
     */
    @NotBlank(message = "API Key不能为空")
    private String apiKey;

    /**
     * 模型名称
     */
    @NotNull
    private String modelName = "qwen3-max";

    /**
     * 是否开启流式响应
     */
    private boolean stream = true;

    /**
     * 超时时间（秒）
     */
    private int timeoutSeconds = 60;

    /**
     * Agent名称映射
     */
    private Map<String, String> names = new HashMap<>();

    /**
     * MCP配置
     */
    private McpProperties mcp = new McpProperties();

    /**
     * Skills配置
     */
    private SkillsProperties skills = new SkillsProperties();

    /**
     * MCP配置内部类
     */
    @Data
    public static class McpProperties {
        /**
         * 百度地图MCP配置
         */
        private BaiduMapProperties baiduMap = new BaiduMapProperties();

        @Data
        public static class BaiduMapProperties {
            /**
             * MCP服务地址
             */
            private String url;

            /**
             * 超时时间（秒）
             */
            private int timeout = 120;
        }
    }

    /**
     * Skills配置内部类
     */
    @Data
    public static class SkillsProperties {
        /**
         * Skills目录路径
         */
        private String dir = "./skills";
    }

    /**
     * 获取Agent名称（带默认值）
     */
    public String getAgentName(String key) {
        return names.getOrDefault(key, key);
    }

    /**
     * 初始化默认Agent名称
     */
    public AgentProperties() {
        names.put("route", "RouteMakingAgent");
        names.put("itinerary", "TripPlannerAgent");
        names.put("budget", "BudgetAgent");
        names.put("manager", "ManagerAgent");
    }
}
