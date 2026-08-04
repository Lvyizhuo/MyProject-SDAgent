package com.shandong.policyagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.UserMessage;

@Slf4j
public class SecurityAdvisor implements BaseAdvisor {

    private static final String NAME = "SecurityAdvisor";
    private static final int DEFAULT_ORDER = 10;

    private final List<String> sensitiveWords;
    private final List<Pattern> sensitivePatterns;
    private final String rejectionMessage;
    private final int order;

    public SecurityAdvisor() {
        this(getDefaultSensitiveWords(), getDefaultSensitivePatterns(),
                "抱歉，您的问题涉及敏感内容，无法回答。请咨询与山东省以旧换新政策相关的问题。",
                DEFAULT_ORDER);
    }

    public SecurityAdvisor(List<String> sensitiveWords, List<Pattern> sensitivePatterns,
                           String rejectionMessage, int order) {
        this.sensitiveWords = sensitiveWords;
        this.sensitivePatterns = sensitivePatterns;
        this.rejectionMessage = rejectionMessage;
        this.order = order;
    }

    /**
     * 默认敏感词列表。
     * <ul>
     *     <li>移除"写代码""编程"：政策咨询系统不应拦截用户正常的编码辅助请求，这两个词误伤率过高。</li>
     *     <li>移除"黑客"：泛词误伤（用户可能在讨论新闻/政策时提及），改为更精确的注入防护模式（见 sensitivePatterns）。</li>
     *     <li>保留"破解"：属于攻击性动作词，正常政策咨询极少出现。</li>
     *     <li>新增"绕过安全限制""忽略安全策略"：精确匹配 Prompt 注入变体，替代泛词"黑客"。</li>
     * </ul>
     */
    private static List<String> getDefaultSensitiveWords() {
        return List.of(
                "政治", "选举", "游行", "示威",
                "忽略上述指令", "忽略之前的指令", "ignore previous", "ignore above",
                "你现在是", "假装你是", "pretend you are",
                "破解", "绕过安全限制", "忽略安全策略"
        );
    }

    private static List<Pattern> getDefaultSensitivePatterns() {
        return List.of(
                Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|prompts?|rules?)"),
                Pattern.compile("(?i)forget\\s+(everything|all|your)"),
                Pattern.compile("(?i)system\\s*:\\s*"),
                Pattern.compile("(?i)\\[\\s*INST\\s*\\]"),
                Pattern.compile("(?i)<\\|im_start\\|>")
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        validateRequest(request);
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        return response;
    }

    private void validateRequest(ChatClientRequest request) {
        String userMessage = extractUserText(request);
        if (userMessage == null || userMessage.isBlank()) {
            return;
        }

        for (String word : sensitiveWords) {
            if (userMessage.contains(word)) {
                log.warn("检测到敏感词 [{}]，拒绝请求: {}", word, truncateMessage(userMessage));
                throw new SecurityException(rejectionMessage);
            }
        }

        for (Pattern pattern : sensitivePatterns) {
            if (pattern.matcher(userMessage).find()) {
                log.warn("检测到敏感模式 [{}]，拒绝请求: {}", pattern.pattern(), truncateMessage(userMessage));
                throw new SecurityException(rejectionMessage);
            }
        }

        log.debug("安全检查通过: {}", truncateMessage(userMessage));
    }

    /**
     * 提取 prompt 中【所有】UserMessage 的文本拼接（含 MessageChatMemoryAdvisor 注入的历史消息）。
     * <p>
     * 旧实现只取 {@code request.prompt().getUserMessage()}（最后一条 UserMessage），
     * 多轮对话中历史 UserMessage 不经安全检查即可传入模型，存在注入绕过面。
     * 现改为遍历 {@code request.prompt().getUserMessages()} 提取全部用户文本拼接检查。
     * </p>
     */
    private String extractUserText(ChatClientRequest request) {
        if (request.prompt() != null && request.prompt().getUserMessages() != null
                && !request.prompt().getUserMessages().isEmpty()) {
            return request.prompt().getUserMessages().stream()
                    .map(UserMessage::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining(" "));
        }
        return null;
    }

    private String truncateMessage(String message) {
        return message.length() > 100 ? message.substring(0, 100) + "..." : message;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> sensitiveWords = getDefaultSensitiveWords();
        private List<Pattern> sensitivePatterns = getDefaultSensitivePatterns();
        private String rejectionMessage = "抱歉，您的问题涉及敏感内容，无法回答。请咨询与山东省以旧换新政策相关的问题。";
        private int order = DEFAULT_ORDER;

        public Builder sensitiveWords(List<String> sensitiveWords) {
            this.sensitiveWords = sensitiveWords;
            return this;
        }

        public Builder sensitivePatterns(List<Pattern> sensitivePatterns) {
            this.sensitivePatterns = sensitivePatterns;
            return this;
        }

        public Builder rejectionMessage(String rejectionMessage) {
            this.rejectionMessage = rejectionMessage;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public SecurityAdvisor build() {
            return new SecurityAdvisor(sensitiveWords, sensitivePatterns, rejectionMessage, order);
        }
    }
}
