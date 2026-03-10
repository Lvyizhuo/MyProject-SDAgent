package com.shandong.policyagent.ingestion.crawler;

import java.util.List;

public interface SiteCrawlerAdapter {

    boolean supports(String url);

    List<CrawledPage> crawl(String url);
}