package com.shandong.policyagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

@Slf4j
public class ReReadingAdvisor implements BaseAdvisor {

    private static final String NAME = "ReReadingAdvisor";
    private static final int DEFAULT_ORDER = 50;

    private static final String DEFAULT_RE2_TEMPLATE = """
            {re2_input_query}
            
            请在回答前仔细核对以下要求：
            1. 如果涉及具体金额、日期、比例等数字，请确保与检索到的政策文档一致
            2. 如果检索到的文档中没有相关信息，请明确告知用户"根据现有政策文档未找到相关信息"
            3. 不要编造或猜测政策内容
            
            请再次阅读用户问题，确保回答准确：{re2_input_query}
            """;

    private final String reReadingTemplate;
    private final int order;

    public ReReadingAdvisor() {
        this(DEFAULT_RE2_TEMPLATE, DEFAULT_ORDER);
    }

    public ReReadingAdvisor(String reReadingTemplate, int order) {
        this.reReadingTemplate = reReadingTemplate;
        this.order = order;
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
        String originalUserText = extractUserText(request);
        if (originalUserText == null || originalUserText.isBlank()) {
            return request;
        }

        String augmentedUserText = PromptTemplate.builder()
                .template(this.reReadingTemplate)
                .variables(Map.of("re2_input_query", originalUserText))
                .build()
                .render();

        log.debug("ReReading augmented user text for accuracy verification");

        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(augmentedUserText))
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        return response;
    }

    private String extractUserText(ChatClientRequest request) {
        if (request.prompt() != null && request.prompt().getUserMessage() != null) {
            return request.prompt().getUserMessage().getText();
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String reReadingTemplate = DEFAULT_RE2_TEMPLATE;
        private int order = DEFAULT_ORDER;

        public Builder reReadingTemplate(String template) {
            this.reReadingTemplate = template;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public ReReadingAdvisor build() {
            return new ReReadingAdvisor(reReadingTemplate, order);
        }
    }
}
