package config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * author: Sinkendlessly
 * description: Agent配置类
 *      启用配置属性
 * date: 2026
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {
    // 配置类，用于启用 AgentProperties
}
