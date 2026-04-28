package utils;

import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CachingAgentCardResolver 缓存降级策略测试。
 * 覆盖：本地缓存命中、Redis二级缓存、Nacos委托解析、Nacos故障降级。
 */
@ExtendWith(MockitoExtension.class)
class CachingAgentCardResolverTest {

    @Mock
    private AgentCardResolver delegate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private AgentCard mockCard;

    private CachingAgentCardResolver resolver;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void testDelegateResultIsCached() {
        when(delegate.getAgentCard("routeAgent")).thenReturn(mockCard);
        resolver = new CachingAgentCardResolver(delegate);

        // 第一次调用 -> 委托 Nacos
        AgentCard result1 = resolver.getAgentCard("routeAgent");
        assertSame(mockCard, result1);
        verify(delegate, times(1)).getAgentCard("routeAgent");

        // 第二次调用 -> 本地缓存命中
        AgentCard result2 = resolver.getAgentCard("routeAgent");
        assertSame(mockCard, result2);
        // delegate 不会被再次调用
        verify(delegate, times(1)).getAgentCard("routeAgent");
    }

    @Test
    void testNacosFailureFallsBackToLocalCache() {
        when(delegate.getAgentCard("routeAgent"))
                .thenReturn(mockCard)   // 第一次成功
                .thenThrow(new RuntimeException("Nacos 连接失败")); // 后续失败

        resolver = new CachingAgentCardResolver(delegate);

        // 第一次成功 -> 缓存
        assertNotNull(resolver.getAgentCard("routeAgent"));

        // 第二次 Nacos 失败 -> 本地缓存命中
        AgentCard cached = resolver.getAgentCard("routeAgent");
        assertSame(mockCard, cached);
    }

    @Test
    void testRedisFallbackWhenNacosFails() {
        // Redis 有缓存数据，Nacos 不会被调用
        when(valueOps.get("atplan:agent:card:routeAgent")).thenReturn(mockCard);

        resolver = new CachingAgentCardResolver(delegate, redisTemplate);

        AgentCard result = resolver.getAgentCard("routeAgent");
        assertSame(mockCard, result);
        verify(valueOps).get("atplan:agent:card:routeAgent");
    }

    @Test
    void testRedisCachedResultIsPromotedToLocal() {
        when(valueOps.get("atplan:agent:card:routeAgent")).thenReturn(mockCard);

        resolver = new CachingAgentCardResolver(delegate, redisTemplate);

        // 第一次 -> Redis 命中，提升到本地缓存
        assertNotNull(resolver.getAgentCard("routeAgent"));

        // 重置 Redis mock 统计，确认第二次不查 Redis
        clearInvocations(valueOps);
        assertNotNull(resolver.getAgentCard("routeAgent"));
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void testBothCacheMissWhenNacosFails() {
        when(delegate.getAgentCard("unknownAgent"))
                .thenThrow(new RuntimeException("Nacos 不可用"));
        when(valueOps.get("atplan:agent:card:unknownAgent")).thenReturn(null);

        resolver = new CachingAgentCardResolver(delegate, redisTemplate);

        AgentCard result = resolver.getAgentCard("unknownAgent");
        assertNull(result);
    }

    @Test
    void testRedisWriteOnSuccessfulResolve() {
        when(delegate.getAgentCard("routeAgent")).thenReturn(mockCard);

        resolver = new CachingAgentCardResolver(delegate, redisTemplate);

        assertNotNull(resolver.getAgentCard("routeAgent"));
        verify(valueOps).set(eq("atplan:agent:card:routeAgent"), eq(mockCard),
                eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    void testNullDelegateDoesNotCrash() {
        resolver = new CachingAgentCardResolver(null);

        AgentCard result = resolver.getAgentCard("anyAgent");
        assertNull(result);
    }

    @Test
    void testClearLocalCache() {
        when(delegate.getAgentCard("routeAgent")).thenReturn(mockCard);
        resolver = new CachingAgentCardResolver(delegate);

        assertNotNull(resolver.getAgentCard("routeAgent"));
        resolver.clearLocalCache();

        // 清空后再次调用应委托 Nacos
        resolver.getAgentCard("routeAgent");
        verify(delegate, times(2)).getAgentCard("routeAgent");
    }
}
