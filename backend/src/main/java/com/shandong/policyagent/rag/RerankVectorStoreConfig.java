package com.shandong.policyagent.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
public class RerankVectorStoreConfig {

    @Bean
    @Primary
    public VectorStore rerankVectorStore(
            @Qualifier("vectorStore") VectorStore delegate,
            DashScopeRerankService rerankService,
            RagConfig ragConfig) {
        return new RerankingVectorStore(delegate, rerankService, ragConfig);
    }
}
