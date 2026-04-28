package utils;

import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.a2a.spec.AgentCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * AgentCard 解析器装饰器：本地缓存 + Redis 二级降级。
 * 当 Nacos 不可用时，自动使用已缓存的 AgentCard 继续服务。
 */
@Slf4j
public class CachingAgentCardResolver implements AgentCardResolver {

    private final AgentCardResolver delegate;
    private final Map<String, AgentCard> cache = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_AGENT_PREFIX = "atplan:agent:card:";
    private static final long CACHE_TTL_HOURS = 1;

    public CachingAgentCardResolver(AgentCardResolver delegate) {
        this(delegate, null);
    }

    public CachingAgentCardResolver(AgentCardResolver delegate, RedisTemplate<String, Object> redisTemplate) {
        this.delegate = delegate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AgentCard getAgentCard(String name) {
        // 1. 本地缓存（最快）
        AgentCard cached = cache.get(name);
        if (cached != null) {
            return cached;
        }

        // 2. Redis 二级缓存（跨进程共享）
        if (redisTemplate != null) {
            try {
                AgentCard redisCached = (AgentCard) redisTemplate.opsForValue()
                        .get(REDIS_AGENT_PREFIX + name);
                if (redisCached != null) {
                    log.debug("[AgentCard] Redis缓存命中: {}", name);
                    cache.put(name, redisCached);
                    return redisCached;
                }
            } catch (Exception e) {
                log.warn("[AgentCard] Redis读取失败: {}", e.getMessage());
            }
        }

        // 3. 委托 Nacos 解析
        if (delegate != null) {
            try {
                AgentCard card = delegate.getAgentCard(name);
                if (card != null) {
                    cache.put(name, card);
                    if (redisTemplate != null) {
                        try {
                            redisTemplate.opsForValue().set(
                                    REDIS_AGENT_PREFIX + name, card,
                                    CACHE_TTL_HOURS, TimeUnit.HOURS);
                        } catch (Exception e) {
                            log.warn("[AgentCard] Redis写入失败: {}", e.getMessage());
                        }
                    }
                    log.debug("[AgentCard] Nacos解析成功: {}", name);
                }
                return card;
            } catch (Exception e) {
                log.error("[AgentCard] Nacos解析失败: {}, 降级到缓存", name, e.getMessage());
            }
        }

        log.warn("[AgentCard] 无可用来源解析: {}", name);
        return null;
    }

    /** 清空本地缓存 */
    public void clearLocalCache() {
        cache.clear();
    }
}
