package service;

import model.AgentResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * author: Sinkendlessly
 * description: RouteCacheService单元测试
 *      需要Redis服务运行
 * date: 2026
 */
@SpringBootTest(classes = {RouteCacheService.class})
@TestPropertySource(properties = {
        "spring.redis.host=localhost",
        "spring.redis.port=6379"
})
class RouteCacheServiceTest {

    @Autowired
    private RouteCacheService cacheService;

    @Test
    void testCacheAndRetrieve() {
        // 准备数据
        String origin = "深圳";
        String destination = "惠州";
        AgentResult result = AgentResult.success("RouteAgent", "路线规划结果", 1000);

        // 缓存
        cacheService.cacheRoute(origin, destination, result);

        // 读取
        AgentResult cached = cacheService.getCachedRoute(origin, destination);

        assertNotNull(cached);
        assertEquals("路线规划结果", cached.getContent());
        assertTrue(cacheService.hasCache(origin, destination));
    }

    @Test
    void testCacheMiss() {
        AgentResult cached = cacheService.getCachedRoute("北京", "上海");
        assertNull(cached);
        assertFalse(cacheService.hasCache("北京", "上海"));
    }

    @Test
    void testEvictCache() {
        String origin = "广州";
        String destination = "珠海";
        AgentResult result = AgentResult.success("RouteAgent", "路线", 500);

        cacheService.cacheRoute(origin, destination, result);
        assertTrue(cacheService.hasCache(origin, destination));

        cacheService.evictRoute(origin, destination);
        assertFalse(cacheService.hasCache(origin, destination));
    }

    @Test
    void testDoNotCacheFailedResult() {
        AgentResult failed = AgentResult.failure("RouteAgent", "网络错误", 0);
        cacheService.cacheRoute("A", "B", failed);

        // 失败结果不应被缓存
        assertFalse(cacheService.hasCache("A", "B"));
    }
}
