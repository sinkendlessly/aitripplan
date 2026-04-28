package config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * author: Sinkendlessly
 * description: AgentProperties配置类测试
 * date: 2026
 */
@SpringBootTest(classes = {AgentProperties.class, AgentConfig.class})
@EnableConfigurationProperties(AgentProperties.class)
@TestPropertySource(properties = {
        "agent.api-key=test-api-key",
        "agent.model-name=qwen-turbo",
        "agent.timeout-seconds=30",
        "agent.names.route=CustomRouteAgent"
})
class AgentPropertiesTest {

    @Autowired
    private AgentProperties agentProperties;

    @Test
    void testApiKey() {
        assertEquals("test-api-key", agentProperties.getApiKey());
    }

    @Test
    void testModelName() {
        assertEquals("qwen-turbo", agentProperties.getModelName());
    }

    @Test
    void testTimeoutSeconds() {
        assertEquals(30, agentProperties.getTimeoutSeconds());
    }

    @Test
    void testDefaultStream() {
        assertTrue(agentProperties.isStream());
    }

    @Test
    void testAgentNames() {
        // 自定义的
        assertEquals("CustomRouteAgent", agentProperties.getAgentName("route"));
        // 默认的
        assertEquals("TripPlannerAgent", agentProperties.getAgentName("itinerary"));
        assertEquals("BudgetAgent", agentProperties.getAgentName("budget"));
    }

    @Test
    void testMcpConfig() {
        assertNotNull(agentProperties.getMcp());
        assertNotNull(agentProperties.getMcp().getBaiduMap());
        assertEquals(120, agentProperties.getMcp().getBaiduMap().getTimeout());
    }

    @Test
    void testSkillsConfig() {
        assertNotNull(agentProperties.getSkills());
        assertEquals("./skills", agentProperties.getSkills().getDir());
    }
}
