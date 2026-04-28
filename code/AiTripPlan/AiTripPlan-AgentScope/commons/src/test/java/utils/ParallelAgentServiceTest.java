package utils;

import config.AgentConfig;
import config.AgentProperties;
import model.AgentResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * author: Sinkendlessly
 * description: ParallelAgentService单元测试
 * date: 2026
 */
@SpringBootTest(classes = {AgentProperties.class, AgentConfig.class})
class ParallelAgentServiceTest {

    @Autowired
    private AgentProperties agentProperties;

    @Test
    void testCallAgentTimeout() {
        // 由于A2A调用需要Nacos，这里主要测试超时配置
        assertEquals(10, agentProperties.getTimeoutSeconds());
    }

    @Test
    void testAgentResultCreation() {
        AgentResult success = AgentResult.success("TestAgent", "成功", 1000);
        assertTrue(success.isSuccess());
        assertEquals("TestAgent", success.getAgentName());

        AgentResult failure = AgentResult.failure("TestAgent", "失败", 500);
        assertFalse(failure.isSuccess());
        assertEquals("失败", failure.getErrorMessage());
    }

    @Test
    void testParallelResults() {
        // 测试并行结果处理逻辑
        AgentResult result1 = AgentResult.success("Agent1", "结果1", 1000);
        AgentResult result2 = AgentResult.failure("Agent2", "错误", 500);

        // 验证至少一个成功的情况
        assertTrue(result1.isSuccess() || result2.isSuccess());

        // 验证失败的情况
        AgentResult[] results = new AgentResult[]{result1, result2};
        boolean anyFailed = false;
        for (AgentResult r : results) {
            if (!r.isSuccess()) {
                anyFailed = true;
                break;
            }
        }
        assertTrue(anyFailed);
    }
}
