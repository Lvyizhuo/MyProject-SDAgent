package com.shandong.policyagent.rag;

/**
 * 原始问题提取器。
 *
 * <p><b>为什么需要</b>：RAG 检索的 query 来自 {@code QuestionAnswerAdvisor}，
 * 它拿到的是被 {@code ExecutionPromptBuilder} 增强过的完整用户消息
 * （【用户原始问题】+ 会话事实 + 执行计划 + 执行要求），长度 2000~4000 字符。
 *
 * <p>直接拿它做三件事都会出问题：
 * <ul>
 *   <li><b>嵌入</b>：3000 字符的模板噪声会稀释真实问题的语义向量，检索精度下降，
 *       且嵌入耗时随长度线性增长（实测 140ms → 2700ms 波动）；</li>
 *   <li><b>缓存键</b>：同一问题的不同模板版本会产生不同键；</li>
 *   <li><b>语义缓存匹配</b>：向量被模板主导，改写问法的相似度被稀释，命中率极低。</li>
 * </ul>
 *
 * <p>因此统一先抽出真实用户问题，再用于嵌入与缓存。
 */
public final class RawQueryExtractor {

    /** 原始问题段落的起始标记。 */
    private static final String RAW_QUESTION_MARKER = "【用户原始问题】";

    /** 段落标记，命中即截断。 */
    private static final String[] SECTION_MARKERS = {
            "【会话事实缓存】",
            "【ReAct执行计划",
            "【执行要求】",
            "【检索到的政策",
            "【工具",
    };

    private RawQueryExtractor() {
    }

    /**
     * 从增强后的用户消息中提取原始问题；无法识别时返回入参本身。
     */
    public static String extract(String query) {
        if (query == null) {
            return "";
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        int start = trimmed.indexOf(RAW_QUESTION_MARKER);
        if (start < 0) {
            return trimmed;
        }
        int contentStart = start + RAW_QUESTION_MARKER.length();

        int end = trimmed.length();
        for (String marker : SECTION_MARKERS) {
            int idx = trimmed.indexOf(marker, contentStart);
            if (idx > 0 && idx < end) {
                end = idx;
            }
        }

        String extracted = trimmed.substring(contentStart, end).trim();
        // 去掉首尾可能残留的冒号/空白
        extracted = extracted.replaceFirst("^[：:\\s]+", "").trim();
        return extracted.isEmpty() ? trimmed : extracted;
    }

    /**
     * 提取并限制长度（用于缓存键与语义注册，避免超长键）。
     */
    public static String extractForCache(String query, int maxChars) {
        String extracted = extract(query);
        if (maxChars > 0 && extracted.length() > maxChars) {
            return extracted.substring(0, maxChars);
        }
        return extracted;
    }
}
