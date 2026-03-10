package com.shandong.policyagent.ingestion.crawler;

import com.shandong.policyagent.entity.UrlImportItemType;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record CrawledPage(
        String sourceUrl,
        String sourcePage,
        String sourceSite,
        UrlImportItemType itemType,
        String title,
        LocalDate publishDate,
        String rawHtml,
        String extractedText,
        List<CrawledAttachment> attachments
) {
}