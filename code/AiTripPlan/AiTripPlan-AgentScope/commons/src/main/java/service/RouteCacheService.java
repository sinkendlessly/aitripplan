package service;

import lombok.extern.slf4j.Slf4j;
import model.AgentResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * author: Imooc
 * description: 路线缓存服务
 *      缓存热门路线规划结果，减少AI调用成本
 * date: 2026
 */
@Slf4j
@Service
public class RouteCacheService {

    private static final String ROUTE_CACHE_PREFIX = "atplan:route:";
    private static final long CACHE_TTL_HOURS = 1;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 生成缓存Key
     * 格式: atplan:route:md5(origin_destination)
     */
    private String buildCacheKey(String origin, String destination) {
        String key = origin.trim().toLowerCase() + "_" + destination.trim().toLowerCase();
        // 使用简单哈希代替MD5（简化示例）
        return ROUTE_CACHE_PREFIX + Math.abs(key.hashCode());
    }

    /**
     * 从缓存获取路线
     *
     * @param origin      出发地
     * @param destination 目的地
     * @return 缓存的路线结果，null表示未命中
     */
    public AgentResult getCachedRoute(String origin, String destination) {
        String key = buildCacheKey(origin, destination);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("[缓存命中] 路线: {} -> {}", origin, destination);
                return (AgentResult) cached;
            }
            log.debug("[缓存未命中] 路线: {} -> {}", origin, destination);
            return null;
        } catch (Exception e) {
            log.error("[缓存读取失败] key={}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 缓存路线结果
     *
     * @param origin      出发地
     * @param destination 目的地
     * @param result      路线规划结果
     */
    public void cacheRoute(String origin, String destination, AgentResult result) {
        if (result == null || !result.isSuccess()) {
            log.warn("[缓存跳过] 不缓存失败或空结果");
            return;
        }

        String key = buildCacheKey(origin, destination);
        try {
            redisTemplate.opsForValue().set(key, result, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.info("[缓存写入] 路线: {} -> {}，TTL: {}小时", origin, destination, CACHE_TTL_HOURS);
        } catch (Exception e) {
            log.error("[缓存写入失败] key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 清除路线缓存
     */
    public void evictRoute(String origin, String destination) {
        String key = buildCacheKey(origin, destination);
        try {
            redisTemplate.delete(key);
            log.info("[缓存清除] 路线: {} -> {}", origin, destination);
        } catch (Exception e) {
            log.error("[缓存清除失败] key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 检查缓存是否存在
     */
    public boolean hasCache(String origin, String destination) {
        String key = buildCacheKey(origin, destination);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取缓存统计
     */
    public CacheStats getCacheStats() {
        try {
            Long size = redisTemplate.keys(ROUTE_CACHE_PREFIX + "*").stream().count();
            return new CacheStats(size != null ? size : 0, CACHE_TTL_HOURS);
        } catch (Exception e) {
            return new CacheStats(0, CACHE_TTL_HOURS);
        }
    }

    /**
     * 缓存统计内部类
     */
    public record CacheStats(long count, long ttlHours) {
    }
}
