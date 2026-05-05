package managerAgent.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.RouteCacheService;

import java.util.Map;

/**
 * 管理员接口（受 AdminAuthFilter Basic Auth 保护）
 * 默认凭据：admin / admin123，通过 ADMIN_USER / ADMIN_PASSWORD 环境变量配置
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private RouteCacheService routeCacheService;

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        RouteCacheService.CacheStats stats = routeCacheService.getCacheStats();
        Map<String, Object> result = Map.of(
                "count", stats.count(),
                "ttlHours", stats.ttlHours()
        );
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/cache/route")
    public ResponseEntity<Map<String, String>> evictRouteCache(
            @RequestParam String origin,
            @RequestParam String destination) {
        routeCacheService.evictRoute(origin, destination);
        log.info("[Admin] 手动清除路线缓存: {} -> {}", origin, destination);
        return ResponseEntity.ok(Map.of("message", "缓存已清除"));
    }
}
