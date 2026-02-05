package com.shandong.policyagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Configuration
public class WebSearchTool {

    @Value("${app.websearch.enabled:true}")
    private boolean enabled;

    @Value("${app.websearch.timeout-seconds:10}")
    private int timeoutSeconds;

    private final WebClient webClient;

    public WebSearchTool() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public record SearchRequest(String query, Integer maxResults) {}

    public record SearchResult(String title, String url, String snippet) {}

    public record SearchResponse(
            String query,
            List<SearchResult> results,
            int totalResults,
            String summary
    ) {}

    @Bean
    @Description("联网搜索工具，用于获取最新的政策信息、新闻动态等网络内容。当用户询问最新政策变化、市场价格、新闻事件等需要实时信息时使用此工具。输入搜索关键词(query)和最大结果数(maxResults，默认5条)，返回搜索结果列表。")
    public Function<SearchRequest, SearchResponse> webSearch() {
        return request -> {
            if (!enabled) {
                log.warn("联网搜索功能已禁用");
                return new SearchResponse(
                        request.query(),
                        List.of(),
                        0,
                        "联网搜索功能当前未启用"
                );
            }

            log.info("执行联网搜索 | 关键词={} | 最大结果数={}", 
                    request.query(), 
                    request.maxResults() != null ? request.maxResults() : 5);

            try {
                List<SearchResult> results = performBingSearch(
                        request.query(),
                        request.maxResults() != null ? request.maxResults() : 5
                );

                String summary = buildSummary(request.query(), results);
                
                log.info("联网搜索完成 | 关键词={} | 结果数={}", request.query(), results.size());

                return new SearchResponse(
                        request.query(),
                        results,
                        results.size(),
                        summary
                );
            } catch (Exception e) {
                log.error("联网搜索失败 | 关键词={}", request.query(), e);
                return new SearchResponse(
                        request.query(),
                        List.of(),
                        0,
                        "搜索失败: " + e.getMessage()
                );
            }
        };
    }

    private List<SearchResult> performBingSearch(String query, int maxResults) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String searchUrl = "https://www.bing.com/search?q=" + encodedQuery + "&count=" + maxResults;

        try {
            String html = webClient.get()
                    .uri(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            return parseSearchResults(html, maxResults);
        } catch (Exception e) {
            log.warn("Bing搜索失败，尝试备用方案 | 错误={}", e.getMessage());
            return performFallbackSearch(query, maxResults);
        }
    }

    private List<SearchResult> parseSearchResults(String html, int maxResults) {
        if (html == null || html.isEmpty()) {
            return List.of();
        }

        java.util.ArrayList<SearchResult> results = new java.util.ArrayList<>();

        Pattern titlePattern = Pattern.compile("<h2[^>]*><a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a></h2>");
        Pattern snippetPattern = Pattern.compile("<p[^>]*class=\"[^\"]*b_algoSlug[^\"]*\"[^>]*>([^<]+)</p>");

        Matcher titleMatcher = titlePattern.matcher(html);
        Matcher snippetMatcher = snippetPattern.matcher(html);

        while (titleMatcher.find() && results.size() < maxResults) {
            String url = titleMatcher.group(1);
            String title = cleanHtml(titleMatcher.group(2));
            String snippet = "";
            
            if (snippetMatcher.find()) {
                snippet = cleanHtml(snippetMatcher.group(1));
            }

            if (!url.contains("bing.com") && !url.contains("microsoft.com")) {
                results.add(new SearchResult(title, url, snippet));
            }
        }

        if (results.isEmpty()) {
            results.add(new SearchResult(
                    "搜索结果解析中",
                    "",
                    "已执行搜索，但结果解析可能不完整。建议用户直接访问搜索引擎获取最新信息。"
            ));
        }

        return results;
    }

    private List<SearchResult> performFallbackSearch(String query, int maxResults) {
        return List.of(new SearchResult(
                "搜索服务暂时不可用",
                "",
                String.format("关于'%s'的搜索暂时无法完成，建议用户直接访问官方政策网站或使用其他搜索引擎查询。", query)
        ));
    }

    private String cleanHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .trim();
    }

    private String buildSummary(String query, List<SearchResult> results) {
        if (results.isEmpty()) {
            return String.format("未找到与'%s'相关的搜索结果", query);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【联网搜索结果】关键词：%s，共找到%d条相关信息：\n", query, results.size()));
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(String.format("%d. %s", i + 1, result.title()));
            if (!result.snippet().isEmpty()) {
                sb.append(String.format(" - %s", result.snippet()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
