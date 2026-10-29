package com.shandong.policyagent.ingestion;

import java.util.List;

public interface SiteCrawlerAdapter {

    boolean supports(String url);

    List<CrawledPage> crawl(String url);
}