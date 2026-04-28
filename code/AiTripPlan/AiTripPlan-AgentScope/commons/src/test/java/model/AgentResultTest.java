package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * author: Imooc
 * description: AgentResult单元测试
 * date: 2026
 */
class AgentResultTest {

    @Test
    void testSuccessResult() {
        AgentResult result = AgentResult.success("TestAgent", "成功内容", 1000);

        assertTrue(result.isSuccess());
        assertEquals("TestAgent", result.getAgentName());
        assertEquals("成功内容", result.getContent());
        assertEquals(1000, result.getExecutionTime());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testFailureResult() {
        AgentResult result = AgentResult.failure("TestAgent", "连接超时", 5000);

        assertFalse(result.isSuccess());
        assertEquals("TestAgent", result.getAgentName());
        assertEquals("连接超时", result.getErrorMessage());
        assertEquals(5000, result.getExecutionTime());
        assertNull(result.getContent());
    }

    @Test
    void testSuccessSummary() {
        AgentResult result = AgentResult.success("RouteAgent", "路线规划完成", 2000);
        String summary = result.getSummary();

        assertTrue(summary.contains("RouteAgent"));
        assertTrue(summary.contains("成功"));
        assertTrue(summary.contains("2000ms"));
        assertTrue(summary.contains("路线规划完成"));
    }

    @Test
    void testFailureSummary() {
        AgentResult result = AgentResult.failure("RouteAgent", "网络错误", 1000);
        String summary = result.getSummary();

        assertTrue(summary.contains("RouteAgent"));
        assertTrue(summary.contains("失败"));
        assertTrue(summary.contains("网络错误"));
    }
}
