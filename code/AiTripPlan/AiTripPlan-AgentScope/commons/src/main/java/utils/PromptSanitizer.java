package utils;

import lombok.extern.slf4j.Slf4j;

/**
 * author: Sinkendlessly
 * description: Prompt注入防护工具
 *      过滤用户输入中的越狱指令，防止注入到System Message
 * date: 2026
 */
@Slf4j
public class PromptSanitizer {

    /** 拦截后替换为无害文本 */
    private static final String REDACTED = "[内容已过滤]";

    /** 需要拦截的注入模式（不区分大小写） */
    private static final String[] BLOCKED_PATTERNS = {
            "忽略之前的所有指令",
            "忽略以上所有",
            "忽略所有指令",
            "忽略之前的指令",
            "ignore all previous",
            "ignore all instructions",
            "ignore the above",
            "ignore previous instructions",
            "forget everything",
            "你是一个",
            "你现在是",
            "system message:",
            "system prompt:",
    };

    /**
     * 对用户输入进行清洗，替换掉越狱指令
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = input;
        boolean matched = false;

        for (String pattern : BLOCKED_PATTERNS) {
            if (result.toLowerCase().contains(pattern.toLowerCase())) {
                log.warn("[PromptSanitizer] 检测到注入模式: {}", pattern);
                result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(pattern), REDACTED);
                matched = true;
            }
        }

        if (matched) {
            log.warn("[PromptSanitizer] 输入已被清洗");
        }
        return result;
    }
}
