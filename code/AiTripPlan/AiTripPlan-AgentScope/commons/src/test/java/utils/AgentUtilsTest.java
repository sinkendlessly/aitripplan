package utils;

import config.AgentProperties;
import config.AgentConfig;
import io.agentscope.core.ReActAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * author: Sinkendlessly
 * description: AgentUtils工具类测试
 * date: 2026
 */
@SpringBootTest(classes = {AgentProperties.class, AgentConfig.class, AgentUtils.class})
@TestPropertySource(properties = {
        "agent.api-key=test-api-key",
        "agent.model-name=qwen-turbo"
})
class AgentUtilsTest {

    @Autowired
    private AgentUtils agentUtils;

    @Test
    void testGetProperties() {
        AgentProperties props = AgentUtils.getProperties();
        assertNotNull(props);
        assertEquals("test-api-key", props.getApiKey());
    }

    @Test
    void testGetReActAgentBuilder() {
        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                "TestAgent",
                "测试Agent"
        );

        assertNotNull(builder);
    }

    @Test
    void testGetReActAgentBuilderWithSkills() {
        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                "TestAgent",
                "测试Agent",
                "route-planning"
        );

        assertNotNull(builder);
    }

    @Test
    void testGetAgentName() {
        String name = AgentUtils.getAgentName("route");
        assertNotNull(name);
    }

    @Test
    void testGetTimeoutSeconds() {
        int timeout = AgentUtils.getTimeoutSeconds();
        assertTrue(timeout > 0);
    }
}
