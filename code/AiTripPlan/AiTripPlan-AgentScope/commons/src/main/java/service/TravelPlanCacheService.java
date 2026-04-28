package service;

import lombok.extern.slf4j.Slf4j;
import model.TravelPlanContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * author: Sinkendlessly
 * description: 旅行规划缓存服务
 *      缓存完整规划结果，支持按会话ID查询
 * date: 2026
 */
@Slf4j
@Service
public class TravelPlanCacheService {

    private static final String PLAN_CACHE_PREFIX = "atplan:session:";
    private static final String HOT_PLAN_KEY = "atplan:hot:plans";
    private static final long SESSION_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建新的规划会话
     *
     * @return 会话ID
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        log.info("[会话创建] sessionId={}", sessionId);
        return sessionId;
    }

    /**
     * 缓存规划结果
     *
     * @param sessionId 会话ID
     * @param context   规划上下文
     */
    public void cachePlan(String sessionId, TravelPlanContext context) {
        String key = PLAN_CACHE_PREFIX + sessionId;
        try {
            redisTemplate.opsForValue().set(key, context, SESSION_TTL_HOURS, TimeUnit.HOURS);

            // 添加到热门规划集合（用于统计）
            redisTemplate.opsForZSet().add(HOT_PLAN_KEY, sessionId, System.currentTimeMillis());

            log.info("[规划缓存] sessionId={}，TTL={}小时", sessionId, SESSION_TTL_HOURS);
        } catch (Exception e) {
            log.error("[规划缓存失败] sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取规划结果
     *
     * @param sessionId 会话ID
     * @return 规划上下文
     */
    public TravelPlanContext getPlan(String sessionId) {
        String key = PLAN_CACHE_PREFIX + sessionId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("[规划查询命中] sessionId={}", sessionId);
                return (TravelPlanContext) cached;
            }
            log.warn("[规划查询未命中] sessionId={}", sessionId);
            return null;
        } catch (Exception e) {
            log.error("[规划查询失败] sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 更新规划结果
     */
    public void updatePlan(String sessionId, TravelPlanContext context) {
        cachePlan(sessionId, context);
    }

    /**
     * 删除规划缓存
     */
    public void deletePlan(String sessionId) {
        String key = PLAN_CACHE_PREFIX + sessionId;
        try {
            redisTemplate.delete(key);
            redisTemplate.opsForZSet().remove(HOT_PLAN_KEY, sessionId);
            log.info("[规划删除] sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[规划删除失败] sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 延长会话有效期
     */
    public void extendSession(String sessionId) {
        String key = PLAN_CACHE_PREFIX + sessionId;
        try {
            redisTemplate.expire(key, SESSION_TTL_HOURS, TimeUnit.HOURS);
            log.debug("[会话续期] sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[会话续期失败] sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取会话数量
     */
    public long getSessionCount() {
        try {
            Long count = redisTemplate.keys(PLAN_CACHE_PREFIX + "*").stream().count();
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取热门路线（按查询频次）
     */
    public long getHotPlanCount() {
        try {
            Long count = redisTemplate.opsForZSet().size(HOT_PLAN_KEY);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
