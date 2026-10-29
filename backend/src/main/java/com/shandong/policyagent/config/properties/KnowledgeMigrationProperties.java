package com.shandong.policyagent.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.knowledge.migration")
public class KnowledgeMigrationProperties {

    private boolean enabled;
    private String targetModel = "ollama:bge-m3";
}
