package model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 路线规划结构化输出契约
 * RouteMakingAgent 的输出应尽量遵循此结构，确保下游 Agent 可解析
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlan {
    private String origin;
    private String destination;
    private Integer totalDistanceKm;
    private Integer estimatedDurationMin;
    private String transportMode;
    private List<RouteSegment> segments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteSegment {
        private String from;
        private String to;
        private String roadName;
        private Integer distanceKm;
        private Integer durationMin;
        private String description;
    }
}
