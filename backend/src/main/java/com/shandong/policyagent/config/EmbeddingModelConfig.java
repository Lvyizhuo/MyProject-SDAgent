package com.shandong.policyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.knowledge.embedding")
public class EmbeddingModelConfig {

    private String defaultModel;
    private List<EmbeddingModel> models = new ArrayList<>();

    @Data
    public static class EmbeddingModel {
        private String id;
        private String provider;
        private String baseUrl;
        private String modelName;
        private String apiKey;
        private Integer dimensions;
        private String vectorTable;
        private Integer maxInputChars;
    }
}
