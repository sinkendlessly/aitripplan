package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * author: Sinkendlessly
 * description: Agent输出JSON校验工具
 *      校验LLM返回的JSON是否包含必要字段
 *      校验不通过不阻断流程，仅记录警告，前端会展示原始文本兜底
 * date: 2026
 */
@Slf4j
public class JsonValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static boolean isValidRoute(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            boolean valid = root.has("origin") && !root.get("origin").isNull()
                    && root.has("destination") && !root.get("destination").isNull()
                    && root.has("totalDistanceKm");
            if (!valid) {
                log.warn("[JsonValidator] 路线JSON缺少必要字段");
            }
            return valid;
        } catch (Exception e) {
            log.warn("[JsonValidator] 路线JSON解析失败: {}", e.getMessage());
            return false;
        }
    }

    public static boolean isValidItinerary(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            boolean valid = root.has("totalDays") && root.has("days") && root.get("days").isArray()
                    && root.get("days").size() > 0;
            if (!valid) {
                log.warn("[JsonValidator] 行程JSON缺少必要字段");
            }
            return valid;
        } catch (Exception e) {
            log.warn("[JsonValidator] 行程JSON解析失败: {}", e.getMessage());
            return false;
        }
    }

    public static boolean isValidBudget(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            boolean valid = root.has("total") && !root.get("total").isNull()
                    && root.has("breakdown") && !root.get("breakdown").isNull();
            if (!valid) {
                log.warn("[JsonValidator] 预算JSON缺少必要字段");
            }
            return valid;
        } catch (Exception e) {
            log.warn("[JsonValidator] 预算JSON解析失败: {}", e.getMessage());
            return false;
        }
    }
}
