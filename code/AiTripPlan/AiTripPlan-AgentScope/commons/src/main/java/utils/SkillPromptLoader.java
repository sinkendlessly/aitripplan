package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * author: Imooc
 * description: Skills 提示词加载器（从本地 skills 目录读取 SKILL.md 并注入到 Agent 描述）
 * date: 2026
 */
public class SkillPromptLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillPromptLoader.class);

    private static final String SKILL_ENV = "AGENT_SKILLS_DIR";
    private static final String SKILL_PROP = "agent.skills.dir";

    private SkillPromptLoader() {
    }

    public static String enrichDescription(String baseDescription, String... skillNames) {
        if (skillNames == null || skillNames.length == 0) {
            return baseDescription;
        }

        List<String> snippets = new ArrayList<>();
        for (String skillName : skillNames) {
            if (skillName == null || skillName.isBlank()) {
                continue;
            }
            loadSkillSnippet(skillName.trim()).ifPresent(snippets::add);
        }

        if (snippets.isEmpty()) {
            return baseDescription;
        }

        return baseDescription
                + "\n\n【技能执行规范】\n"
                + String.join("\n\n", snippets)
                + "\n\n请严格遵循以上技能规范输出，若信息缺失先提出最少且关键的问题。";
    }

    public static Optional<String> loadSkillSnippet(String skillName) {
        Optional<Path> skillFile = locateSkillFile(skillName);
        if (skillFile.isEmpty()) {
            log.warn("未找到 Skill 文件: {}", skillName);
            return Optional.empty();
        }

        try {
            String content = Files.readString(skillFile.get(), StandardCharsets.UTF_8);
            String description = extractFrontMatterDescription(content).orElse("暂无描述");
            String summary = extractTopSummary(content, 14);

            String snippet = "- Skill: " + skillName + "\n"
                    + "  - 描述: " + description + "\n"
                    + "  - 核心规则摘要: " + summary;

            return Optional.of(snippet);
        }
        catch (IOException e) {
            log.error("读取 Skill 文件失败: {}", skillName, e);
            return Optional.empty();
        }
    }

    private static Optional<Path> locateSkillFile(String skillName) {
        Optional<Path> root = resolveSkillsRoot();
        if (root.isEmpty()) {
            return Optional.empty();
        }

        Path folderSkillFile = root.get().resolve(skillName).resolve("SKILL.md");
        if (Files.exists(folderSkillFile) && Files.isRegularFile(folderSkillFile)) {
            return Optional.of(folderSkillFile);
        }

        Path rootMarkdownFile = root.get().resolve(skillName + ".md");
        if (Files.exists(rootMarkdownFile) && Files.isRegularFile(rootMarkdownFile)) {
            return Optional.of(rootMarkdownFile);
        }

        return Optional.empty();

    }

    private static Optional<Path> resolveSkillsRoot() {
        List<Path> candidates = new ArrayList<>();

        String propPath = System.getProperty(SKILL_PROP);
        if (propPath != null && !propPath.isBlank()) {
            candidates.add(Paths.get(propPath));
        }

        String envPath = System.getenv(SKILL_ENV);
        if (envPath != null && !envPath.isBlank()) {
            candidates.add(Paths.get(envPath));
        }

        Path cwd = Paths.get(System.getProperty("user.dir"));
        candidates.add(cwd.resolve("skills"));

        Path current = cwd;
        for (int i = 0; i < 8 && current != null; i++) {
            candidates.add(current.resolve("skills"));
            current = current.getParent();
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate) && Files.isDirectory(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    private static Optional<String> extractFrontMatterDescription(String content) {
        Pattern pattern = Pattern.compile("(?s)^---\\s*(.*?)\\s*---");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String frontMatter = matcher.group(1);
        return Arrays.stream(frontMatter.split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith("description:"))
                .map(line -> line.substring("description:".length()).trim())
                .findFirst();
    }

    private static String extractTopSummary(String content, int maxLines) {
        String body = removeFrontMatter(content);

        List<String> lines = Arrays.stream(body.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .limit(maxLines)
                .toList();

        String summary = String.join("；", lines);
        if (summary.length() > 260) {
            return summary.substring(0, 260) + "...";
        }
        return summary;
    }

    private static String removeFrontMatter(String content) {
        Pattern pattern = Pattern.compile("(?s)^---\\s*.*?\\s*---\\s*");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return content.substring(matcher.end());
        }
        return content;
    }
}
