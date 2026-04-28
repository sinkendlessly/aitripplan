package utils;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Agent 发现服务（单例）。
 * 封装 Nacos 服务发现 + {@link CachingAgentCardResolver} 二级缓存降级。
 * 所有 Agent 创建统一使用此服务，避免 Nacos 单点故障时服务完全不可用。
 */
@Slf4j
@Service
public class AgentDiscoveryService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private volatile CachingAgentCardResolver resolver;

    private CachingAgentCardResolver getOrInitResolver() {
        if (resolver == null) {
            synchronized (this) {
                if (resolver == null) {
                    try {
                        var nacosResolver = new NacosAgentCardResolver(NacosUtil.getNacosClient());
                        resolver = new CachingAgentCardResolver(nacosResolver, redisTemplate);
                        log.info("AgentDiscoveryService 初始化完成，Redis二级降级: {}",
                                redisTemplate != null ? "已启用" : "未启用");
                    } catch (Exception e) {
                        log.warn("Nacos 初始化失败，Agent 发现将在 Nacos 可用后重试: {}", e.getMessage());
                        // 创建一个无委托的解析器，仅尝试 Redis 缓存
                        resolver = new CachingAgentCardResolver(null, redisTemplate);
                    }
                }
            }
        }
        return resolver;
    }

    /**
     * 创建 A2aAgent（使用缓存的 AgentCardResolver）
     */
    public A2aAgent createAgent(String agentName) {
        return A2aAgent.builder()
                .name(agentName)
                .agentCardResolver(getOrInitResolver())
                .build();
    }

    /**
     * 获取共享的 CachingAgentCardResolver（供静态方法使用）
     */
    public static CachingAgentCardResolver getSharedResolver() {
        return SpringContextHolder.getBean(AgentDiscoveryService.class).getOrInitResolver();
    }

    @PreDestroy
    public void clear() {
        if (resolver != null) {
            resolver.clearLocalCache();
        }
    }
}
