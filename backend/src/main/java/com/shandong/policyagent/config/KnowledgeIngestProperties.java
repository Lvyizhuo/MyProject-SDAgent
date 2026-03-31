package com.shandong.policyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.knowledge.ingest")
public class KnowledgeIngestProperties {

    /**
     * Whether the scheduler pulls pending documents for ingestion.
     */
    private boolean schedulerEnabled = true;

    /**
     * Max number of documents processed concurrently.
     */
    private int maxConcurrent = 2;

    /**
     * Max number of documents scheduled per minute. Set 0 to disable rate limit.
     */
    private int maxPerMinute = 30;

    /**
     * Max number of pending documents pulled per poll.
     */
    private int batchSize = 20;

    /**
     * Poll interval for the scheduler.
     */
    private long pollIntervalMs = 2000;
}
