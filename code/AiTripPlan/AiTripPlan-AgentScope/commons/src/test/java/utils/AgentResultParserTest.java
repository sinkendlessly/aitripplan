package utils;

import model.RoutePlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentResultParser 多策略提取测试。
 * 覆盖：代码块、JSON对象、JSON数组、启发式扫描 四种策略。
 */
class AgentResultParserTest {

    @Test
    void testExtractFromCodeBlock() {
        String text = """
                以下是路线规划方案：
                从深圳到惠州的路线推荐。

                ```json
                {
                  "origin": "深圳",
                  "destination": "惠州",
                  "totalDistanceKm": 120,
                  "estimatedDurationMin": 150,
                  "transportMode": "自驾"
                }
                ```
                希望以上方案对您有帮助。
                """;

        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNotNull(plan);
        assertEquals("深圳", plan.getOrigin());
        assertEquals("惠州", plan.getDestination());
        assertEquals(120, plan.getTotalDistanceKm());
        assertEquals(150, plan.getEstimatedDurationMin());
        assertEquals("自驾", plan.getTransportMode());
    }

    @Test
    void testExtractFromBareObject() {
        String text = """
                根据您的需求，路线方案如下：
                {"origin": "广州", "destination": "珠海", "totalDistanceKm": 160, "estimatedDurationMin": 180, "transportMode": "自驾"}
                祝旅途愉快！
                """;

        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNotNull(plan);
        assertEquals("广州", plan.getOrigin());
        assertEquals("珠海", plan.getDestination());
        assertEquals(160, plan.getTotalDistanceKm());
    }

    @Test
    void testExtractFromNestedBrackets() {
        // LLM 输出中可能包含多个花括号，parser 应该匹配最外层
        String text = """
                方案详情：
                {"origin": "东莞", "destination": "深圳", "totalDistanceKm": 80, "estimatedDurationMin": 90, "transportMode": "自驾", "segments": [{"from": "东莞", "to": "深圳", "roadName": "G94", "distanceKm": 80, "durationMin": 90}]}
                以上为推荐路线。
                """;

        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNotNull(plan);
        assertEquals("东莞", plan.getOrigin());
        assertNotNull(plan.getSegments());
        assertEquals(1, plan.getSegments().size());
        assertEquals("G94", plan.getSegments().get(0).getRoadName());
    }

    @Test
    void testExtractFromAnyJsonFallback() {
        // 文本中有其他花括号内容，第一个不可解析应跳过
        String text = """
                处理结果：{invalid} 正确结果在下面
                最终方案：{"origin": "佛山", "destination": "中山", "totalDistanceKm": 90, "estimatedDurationMin": 120, "transportMode": "自驾"}
                """;

        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNotNull(plan);
        assertEquals("佛山", plan.getOrigin());
    }

    @Test
    void testNullInput() {
        assertNull(AgentResultParser.tryExtract(null, RoutePlan.class));
    }

    @Test
    void testBlankInput() {
        assertNull(AgentResultParser.tryExtract("   ", RoutePlan.class));
    }

    @Test
    void testNoJsonInText() {
        String text = "纯文本输出，没有任何JSON结构。";
        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNull(plan);
    }

    @Test
    void testSingleQuotes() {
        // ALLOW_SINGLE_QUOTES 功能验证
        String text = "{'origin': '北京', 'destination': '上海', 'totalDistanceKm': 1200, 'estimatedDurationMin': 600, 'transportMode': '高铁'}";

        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNotNull(plan);
        assertEquals("北京", plan.getOrigin());
        assertEquals("上海", plan.getDestination());
    }

    @Test
    void testTrailingComma() {
        // ALLOW_TRAILING_COMMA 功能验证
        String text = """
                {"origin": "杭州", "destination": "苏州", "totalDistanceKm": 150, "estimatedDurationMin": 120, "transportMode": "自驾",}
                """;

        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNotNull(plan);
        assertEquals("杭州", plan.getOrigin());
    }

    @Test
    void testUnquotedFieldNames() {
        // ALLOW_UNQUOTED_FIELD_NAMES 功能验证
        String text = "{origin: '成都', destination: '重庆', totalDistanceKm: 300, estimatedDurationMin: 240, transportMode: '高铁'}";

        RoutePlan plan = AgentResultParser.tryExtract(text, RoutePlan.class);
        assertNotNull(plan);
        assertEquals("成都", plan.getOrigin());
    }
}
