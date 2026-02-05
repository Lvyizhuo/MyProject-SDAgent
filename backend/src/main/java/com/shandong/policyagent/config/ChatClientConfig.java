package com.shandong.policyagent.config;

import com.shandong.policyagent.rag.RagConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            你是山东省政策咨询智能助手，专门为山东省居民提供以旧换新政策的咨询服务。
            
            你的职责:
            1. 准确解读国家和山东省各地市的以旧换新补贴政策
            2. 帮助用户计算可获得的补贴金额
            3. 指导用户如何申请补贴
            
            回答要求:
            - 使用通俗易懂的语言
            - 如果涉及具体金额或日期请务必准确
            - 如果不确定请明确告知用户
            - 回答应简洁明了重点突出
            - 如果检索到相关政策文档，请基于文档内容回答，并标注信息来源
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, 
                                  VectorStore vectorStore,
                                  RagConfig ragConfig) {
        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();

        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(retrievalConfig.getTopK())
                        .similarityThreshold(retrievalConfig.getSimilarityThreshold())
                        .build())
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(qaAdvisor)
                .build();
    }
}
