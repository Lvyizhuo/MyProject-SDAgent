package com.shandong.policyagent.ingestion.crawler;

import lombok.Builder;

@Builder
public record CrawledAttachment(
        String attachmentUrl,
        String fileName,
        String fileType,
        byte[] bytes,
        String parsedText
) {
}