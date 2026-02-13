package com.shandong.policyagent.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class WebSearchTool {

    @Value("${app.websearch.enabled:true}")
    private boolean enabled;

    @Value("${app.websearch.timeout-seconds:15}")
    private int timeoutSeconds;

    @Value("${app.websearch.tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${app.websearch.tavily.base-url:https://api.tavily.com}")
    private String tavilyBaseUrl;

    @Value("${app.websearch.tavily.search-depth:advanced}")
    private String searchDepth;

    @Value("${app.websearch.tavily.max-results:5}")
    private int defaultMaxResults;

    @Value("${app.websearch.cache-to-vectorstore:true}")
    private boolean cacheToVectorStore;

    private final WebClient webClient;
    private final VectorStore vectorStore;

    public WebSearchTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TavilyResponse(
            String query,
            String answer,
            @JsonProperty("results") List<TavilyResult> results
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TavilyResult(
            String title,
            String url,
            String content,
            double score
    ) {}

    @Bean
    @Description("联网搜索工具，用于搜索最新的产品价格、政策信息、新闻动态等实时网络内容。" +
            "当用户询问具体产品的市场价格（如iPhone、华为手机的售价）、最新政策变化、新闻事件等需要实时信息时，必须调用此工具。" +
            "输入搜索关键词(query)和最大结果数(maxResults，默认5条)，返回搜索结果摘要和详细列表。")
    public Function<SearchRequest, SearchResponse> webSearch() {
        return request -> {
            String query = request != null && request.query() != null ? request.query().trim() : "";
            if (query.isBlank()) {
                return new SearchResponse(
                        "",
                        List.of(),
                        0,
                        "搜索关键词为空，请提供明确的搜索内容，例如：iPhone 17 标准版 价格"
                );
            }

            if (!enabled) {
                log.warn("联网搜索功能已禁用");
                return new SearchResponse(query, List.of(), 0, "联网搜索功能当前未启用");
            }

            if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
                log.error("Tavily API Key 未配置，无法执行联网搜索");
                return new SearchResponse(query, List.of(), 0,
                        "联网搜索服务未配置API密钥，请联系管理员设置 TAVILY_API_KEY 环境变量");
            }

            int maxResults = request.maxResults() != null ? request.maxResults() : defaultMaxResults;
            log.info("执行 Tavily 联网搜索 | 关键词={} | 最大结果数={}", query, maxResults);

            try {
                TavilyResponse tavilyResponse = callTavilyApi(query, maxResults);
                List<SearchResult> results = convertResults(tavilyResponse);
                String summary = buildSummary(query, tavilyResponse, results);

                log.info("联网搜索完成 | 关键词={} | 结果数={}", query, results.size());

                // 将搜索结果缓存到向量数据库
                if (cacheToVectorStore && !results.isEmpty()) {
                    cacheSearchResultToVectorStore(query, tavilyResponse, results);
                }

                return new SearchResponse(query, results, results.size(), summary);

            } catch (Exception e) {
                log.error("联网搜索失败 | 关键词={}", query, e);
                return new SearchResponse(query, List.of(), 0,
                        "搜索失败: " + e.getMessage());
            }
        };
    }

    private TavilyResponse callTavilyApi(String query, int maxResults) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("search_depth", searchDepth);
        requestBody.put("max_results", maxResults);
        requestBody.put("include_answer", true);

        return webClient.post()
                .uri(tavilyBaseUrl + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .header("Authorization", "Bearer " + tavilyApiKey)
                .retrieve()
                .bodyToMono(TavilyResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private List<SearchResult> convertResults(TavilyResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .map(r -> new SearchResult(
                        r.title() != null ? r.title() : "",
                        r.url() != null ? r.url() : "",
                        r.content() != null ? truncate(r.content(), 300) : ""
                ))
                .collect(Collectors.toList());
    }

    private String buildSummary(String query, TavilyResponse tavilyResponse, List<SearchResult> results) {
        if (results.isEmpty()) {
            return String.format("未找到与'%s'相关的搜索结果", query);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【联网搜索结果】关键词：%s，共找到%d条相关信息：\n", query, results.size()));

        if (tavilyResponse.answer() != null && !tavilyResponse.answer().isBlank()) {
            sb.append(String.format("AI摘要：%s\n\n", tavilyResponse.answer()));
        }

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(String.format("%d. %s", i + 1, result.title()));
            if (!result.snippet().isEmpty()) {
                sb.append(String.format(" - %s", result.snippet()));
            }
            if (!result.url().isEmpty()) {
                sb.append(String.format(" (%s)", result.url()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void cacheSearchResultToVectorStore(String query, TavilyResponse tavilyResponse, List<SearchResult> results) {
        try {
            String documentText = buildCacheDocumentText(query, tavilyResponse, results);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "web-search");
            metadata.put("query", query);
            metadata.put("searchedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadata.put("resultCount", results.size());

            String sourceUrls = results.stream()
                    .map(SearchResult::url)
                    .filter(url -> url != null && !url.isEmpty())
                    .collect(Collectors.joining(", "));
            metadata.put("sourceUrls", sourceUrls);

            String documentId = UUID.nameUUIDFromBytes(
                    (query + "|" + sourceUrls).getBytes()).toString();
            Document document = new Document(documentId, documentText, metadata);
            vectorStore.add(List.of(document));

            log.info("搜索结果已缓存到向量数据库 | 关键词={}", query);
        } catch (Exception e) {
            log.warn("搜索结果缓存到向量数据库失败 | 关键词={}", query, e);
        }
    }

    private String buildCacheDocumentText(String query, TavilyResponse tavilyResponse, List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("搜索查询：%s\n", query));
        sb.append(String.format("搜索时间：%s\n\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

        if (tavilyResponse.answer() != null && !tavilyResponse.answer().isBlank()) {
            sb.append(String.format("综合摘要：%s\n\n", tavilyResponse.answer()));
        }

        sb.append("详细搜索结果：\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(String.format("%d. %s\n", i + 1, result.title()));
            if (!result.snippet().isEmpty()) {
                sb.append(String.format("   %s\n", result.snippet()));
            }
            if (!result.url().isEmpty()) {
                sb.append(String.format("   来源：%s\n", result.url()));
            }
        }

        return sb.toString();
    }
}
