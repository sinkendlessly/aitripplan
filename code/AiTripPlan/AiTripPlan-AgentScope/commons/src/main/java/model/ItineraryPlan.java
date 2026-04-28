package model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 行程规划结构化输出契约
 * TripPlannerAgent 的输出应尽量遵循此结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryPlan {
    private Integer totalDays;
    private List<DayPlan> days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayPlan {
        private Integer day;
        private List<Activity> morning;
        private List<Activity> afternoon;
        private List<Activity> evening;
        private String accommodation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Activity {
        private String name;
        private String type;        // scenic_spot, restaurant, hotel, other
        private Integer durationMin;
        private String description;
        private Integer estimatedCost;
    }
}
