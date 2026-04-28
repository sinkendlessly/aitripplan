package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import lombok.extern.slf4j.Slf4j;

/**
 * 从 Agent LLM 输出中提取结构化数据
 * 支持多策略提取：代码块 → JSON对象 → JSON数组 → 启发式扫描
 */
@Slf4j
public class AgentResultParser {

    private static final ObjectMapper mapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    /**
     * 从文本中提取 JSON 并解析为目标类型。
     * 策略：代码块 > JSON对象 > JSON数组 > 启发式 JSON 片段
     */
    public static <T> T tryExtract(String text, Class<T> clazz) {
        if (text == null || text.isBlank()) return null;

        // 策略1: ```json ... ``` 代码块
        T result = tryExtractCodeBlock(text, clazz);
        if (result != null) return result;

        // 策略2: 直接找最外层 {...}
        result = tryExtractObject(text, clazz);
        if (result != null) return result;

        // 策略3: 直接找最外层 [...]
        result = tryExtractArray(text, clazz);
        if (result != null) return result;

        // 策略4: 从任意的 { 开始到最近的 }
        result = tryExtractAnyJson(text, clazz);
        return result;
    }

    /**
     * 从 ```json ... ``` 标记中提取
     */
    public static <T> T tryExtractCodeBlock(String text, Class<T> clazz) {
        try {
            String marker = "```json";
            int start = text.indexOf(marker);
            if (start == -1) return null;

            start = text.indexOf('\n', start) + 1;
            int end = text.indexOf("```", start);
            if (end == -1) return null;

            String json = text.substring(start, end).trim();
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            log.debug("代码块提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取最外层的 {...} JSON 对象
     */
    private static <T> T tryExtractObject(String text, Class<T> clazz) {
        try {
            int start = text.indexOf('{');
            if (start == -1) return null;

            int depth = 0;
            int end = -1;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { end = i; break; }
                }
            }
            if (end == -1) return null;

            String json = text.substring(start, end + 1);
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            log.debug("JSON对象提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取最外层的 [...] JSON 数组
     */
    private static <T> T tryExtractArray(String text, Class<T> clazz) {
        try {
            int start = text.indexOf('[');
            if (start == -1) return null;

            int depth = 0;
            int end = -1;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) { end = i; break; }
                }
            }
            if (end == -1) return null;

            String json = text.substring(start, end + 1);
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            log.debug("JSON数组提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 启发式扫描：寻找任何可以解析为 JSON 的片段
     */
    private static <T> T tryExtractAnyJson(String text, Class<T> clazz) {
        int searchStart = 0;
        while (true) {
            int brace = text.indexOf('{', searchStart);
            if (brace == -1) return null;

            try {
                int depth = 0;
                int end = -1;
                for (int i = brace; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) { end = i; break; }
                    }
                }
                if (end == -1) return null;

                String candidate = text.substring(brace, end + 1);
                return mapper.readValue(candidate, clazz);
            } catch (Exception e) {
                // Move past this brace and try the next one
                searchStart = brace + 1;
            }
        }
    }
}
