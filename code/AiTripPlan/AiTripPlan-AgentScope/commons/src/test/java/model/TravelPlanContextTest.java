package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * author: Imooc
 * description: TravelPlanContext单元测试
 * date: 2026
 */
class TravelPlanContextTest {

    private TravelPlanContext context;

    @BeforeEach
    void setUp() {
        context = TravelPlanContext.builder()
                .userRequest("深圳到惠州3日游")
                .build();
    }

    @Test
    void testHasAnyResult_WithNoResults() {
        assertFalse(context.hasAnyResult());
    }

    @Test
    void testHasAnyResult_WithOneSuccess() {
        context.setRouteResult(AgentResult.success("RouteAgent", "路线", 1000));
        assertTrue(context.hasAnyResult());
    }

    @Test
    void testHasAnyResult_WithOnlyFailures() {
        context.setRouteResult(AgentResult.failure("RouteAgent", "失败", 1000));
        context.setItineraryResult(AgentResult.failure("ItineraryAgent", "失败", 1000));
        assertFalse(context.hasAnyResult());
    }

    @Test
    void testGetSuccessCount() {
        assertEquals(0, context.getSuccessCount());

        context.setRouteResult(AgentResult.success("RouteAgent", "路线", 1000));
        assertEquals(1, context.getSuccessCount());

        context.setItineraryResult(AgentResult.success("ItineraryAgent", "行程", 2000));
        assertEquals(2, context.getSuccessCount());

        context.setBudgetResult(AgentResult.failure("BudgetAgent", "失败", 500));
        assertEquals(2, context.getSuccessCount()); // 失败不计数
    }

    @Test
    void testGetFullSummary() {
        context.setRouteResult(AgentResult.success("RouteAgent", "深圳->惠州", 1000));
        context.setItineraryResult(AgentResult.success("ItineraryAgent", "Day1: 西湖", 2000));

        String summary = context.getFullSummary();

        assertTrue(summary.contains("深圳到惠州3日游"));
        assertTrue(summary.contains("路线规划"));
        assertTrue(summary.contains("行程规划"));
    }

    @Test
    void testExtrasMap() {
        context.getExtras().put("budget", 5000);
        context.getExtras().put("people", 2);

        assertEquals(5000, context.getExtras().get("budget"));
        assertEquals(2, context.getExtras().get("people"));
    }
}
