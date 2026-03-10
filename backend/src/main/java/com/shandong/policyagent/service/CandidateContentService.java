package com.shandong.policyagent.service;

import com.shandong.policyagent.ingestion.crawler.CrawledPage;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CandidateContentService {

    private static final Pattern REGION_PATTERN = Pattern.compile("(济南|青岛|淄博|枣庄|东营|烟台|潍坊|济宁|泰安|威海|日照|临沂|德州|聊城|滨州|菏泽)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("20\\d{2}");
    private static final String PROVINCE_TAG = "山东省";
    private static final Set<String> POLICY_KEYWORDS = Set.of("以旧换新", "补贴", "通知", "公告", "方案", "细则", "实施", "办法", "指南", "政策");
    private static final Set<String> LOW_PRIORITY_KEYWORDS = Set.of("启动仪式", "圆满成功", "接力赛", "消费季", "走进", "活动现场", "宣贯活动");
    private static final Set<String> NOISE_PATTERNS = Set.of("当前位置", "网站声明", "隐私保护", "版权所有", "联系我们", "网站地图", "打印", "关闭窗口", "分享到");

    public CandidateEvaluation evaluate(CrawledPage page) {
        String cleanedText = cleanText(page.extractedText());
        if (cleanedText.isBlank()) {
            return CandidateEvaluation.builder()
                    .cleanedText("")
                    .summary("")
                    .category("")
                    .tags(List.of())
                    .qualityScore(0)
                    .shouldKeep(false)
                    .reviewComment("正文清洗后为空")
                    .contentHash("")
                    .build();
        }

        int score = score(page.title(), cleanedText, page.publishDate());
        String category = inferCategory(page.title(), cleanedText);
        List<String> tags = inferTags(page.title(), cleanedText);
        String summary = inferSummary(cleanedText);
        boolean shouldKeep = score >= 60;
        String reviewComment = score < 60 ? "质量评分不足，已过滤" : "";

        return CandidateEvaluation.builder()
                .cleanedText(cleanedText)
                .summary(summary)
                .category(category)
                .tags(tags)
                .qualityScore(score)
                .shouldKeep(shouldKeep)
                .reviewComment(reviewComment)
                .contentHash(hash(cleanedText))
                .build();
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n");

        List<String> lines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawLine : normalized.split("\\n")) {
            String line = rawLine.replaceAll("\\s+", " ").trim();
            if (line.isBlank()) {
                continue;
            }
            if (containsAny(line, NOISE_PATTERNS)) {
                continue;
            }
            if (seen.add(line)) {
                lines.add(line);
            }
        }
        return String.join("\n\n", lines).trim();
    }

    private int score(String title, String cleanedText, LocalDate publishDate) {
        int score = 0;
        String haystack = (title + " " + cleanedText).toLowerCase(Locale.ROOT);
        for (String keyword : POLICY_KEYWORDS) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 12;
            }
        }
        for (String keyword : LOW_PRIORITY_KEYWORDS) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                score -= 10;
            }
        }
        if (cleanedText.length() > 300) {
            score += 10;
        }
        if (cleanedText.length() > 1000) {
            score += 10;
        }
        if (publishDate != null) {
            score += 10;
        }
        if (containsAny(cleanedText, Set.of("实施方案", "实施细则", "工作方案", "公告", "通知"))) {
            score += 15;
        }
        if (cleanedText.length() < 120) {
            score -= 25;
        }
        return Math.max(0, Math.min(score, 100));
    }

    private String inferCategory(String title, String text) {
        String haystack = (title + " " + text).toLowerCase(Locale.ROOT);
        if (containsAny(haystack, Set.of("实施细则", "细则", "办法", "规程"))) {
            return "实施细则";
        }
        if (containsAny(haystack, Set.of("通知", "公告", "通告"))) {
            return "通知公告";
        }
        if (containsAny(haystack, Set.of("解读", "问答", "答疑"))) {
            return "政策解读";
        }
        if (containsAny(haystack, Set.of("补贴", "以旧换新", "家电", "消费券"))) {
            return "补贴政策";
        }
        return "政策文件";
    }

    private List<String> inferTags(String title, String text) {
        String haystack = title + " " + text;
        Set<String> tags = new LinkedHashSet<>();
        if (containsAny(haystack, Set.of("山东", "山东省", "山东省商务厅"))) {
            tags.add(PROVINCE_TAG);
        }
        if (containsAny(haystack, Set.of("以旧换新"))) {
            tags.add("以旧换新");
        }
        if (containsAny(haystack, Set.of("家电"))) {
            tags.add("家电");
        }
        if (containsAny(haystack, Set.of("汽车"))) {
            tags.add("汽车");
        }
        if (containsAny(haystack, Set.of("手机", "数码"))) {
            tags.add("数码产品");
        }
        Matcher regionMatcher = REGION_PATTERN.matcher(haystack);
        if (regionMatcher.find()) {
            tags.add(regionMatcher.group(1) + "市");
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(haystack);
        if (yearMatcher.find()) {
            tags.add(yearMatcher.group());
        }
        return new ArrayList<>(tags);
    }

    private String inferSummary(String text) {
        String condensed = text.replaceAll("\\s+", " ").trim();
        if (condensed.length() <= 180) {
            return condensed;
        }
        return condensed.substring(0, 180) + "...";
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成内容哈希失败", e);
        }
    }

    private boolean containsAny(String text, Set<String> patterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (haystack.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @Builder
    public record CandidateEvaluation(
            String cleanedText,
            String summary,
            String category,
            List<String> tags,
            Integer qualityScore,
            Boolean shouldKeep,
            String reviewComment,
            String contentHash
    ) {
    }
}