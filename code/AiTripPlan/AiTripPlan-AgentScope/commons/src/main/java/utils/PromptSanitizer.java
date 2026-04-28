package utils;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 用户输入安全过滤工具
 * 在用户输入到达 LLM 前进行清洗和分隔，降低 prompt injection 风险
 */
@Slf4j
public class PromptSanitizer {

    private static final int MAX_PROMPT_LENGTH = 4000;

    // 常见注入关键词检测
    private static final Pattern[] INJECTION_PATTERNS = {
            Pattern.compile("ignore\\s+(all\\s+)?(previous|above|below)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(all\\s+)?(previous|above)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("你.+忽略.+指令", Pattern.UNICODE_CASE),
            Pattern.compile("你.+扮演", Pattern.UNICODE_CASE),
    };

    /**
     * 对用户输入进行安全处理
     * 1. 截断超长输入
     * 2. 用分隔标记包裹，明确区分系统指令和用户输入
     */
    public static String sanitize(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return "";
        }

        // 截断超长输入
        String trimmed = rawInput.length() > MAX_PROMPT_LENGTH
                ? rawInput.substring(0, MAX_PROMPT_LENGTH)
                : rawInput;

        // 将用户输入用明确的分隔标记包裹
        return "【用户输入开始】\n" + trimmed + "\n【用户输入结束】";
    }

    /**
     * 检测是否存在注入风险（仅日志告警，不阻断）
     */
    public static boolean hasSuspiciousContent(String input) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("[安全告警] 用户输入包含可疑内容，匹配模式: {}", pattern);
                return true;
            }
        }
        return false;
    }
}
