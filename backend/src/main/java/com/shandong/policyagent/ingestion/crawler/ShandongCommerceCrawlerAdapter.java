package com.shandong.policyagent.ingestion.crawler;

import com.shandong.policyagent.entity.UrlImportItemType;
import com.shandong.policyagent.rag.DocumentLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShandongCommerceCrawlerAdapter implements SiteCrawlerAdapter {

    private static final String SOURCE_SITE = "山东省商务厅";
    private static final String HOST = "commerce.shandong.gov.cn";
    private static final int MAX_TOTAL_CANDIDATES = 240;
    private static final int MAX_RECORDS_PER_COLUMN = 60;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; PolicyAgent/1.0; +https://github.com/)";
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[年\\-/\\.](\\d{1,2})[月\\-/\\.](\\d{1,2})");
    private static final Pattern TOTAL_RECORD_PATTERN = Pattern.compile("totalRecord:(\\d+)");
    private static final Pattern PER_PAGE_PATTERN = Pattern.compile("perPage:(\\d+)");
    private static final Pattern PROXY_URL_PATTERN = Pattern.compile("proxyUrl:'([^']+)'");
    private static final Pattern PARAM_PATTERN = Pattern.compile("var\\s+param_(\\d+)\\s*=\\s*\\{col:(\\d+),webid:(\\d+),path:'([^']+)',columnid:(\\d+),sourceContentType:(\\d+),unitid:'([^']+)',webname:'([^']+)',permissiontype:(\\d+)\\}");
    private static final Set<String> POLICY_KEYWORDS = Set.of("以旧换新", "补贴", "通知", "公告", "方案", "细则", "实施", "办法", "指南", "政策");
    private static final Set<String> LOW_PRIORITY_KEYWORDS = Set.of("启动仪式", "圆满成功", "接力赛", "消费季", "走进", "活动现场", "宣贯活动");
    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of("pdf", "doc", "docx");

        private final DocumentLoaderService documentLoaderService;
        private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public boolean supports(String url) {
        try {
            URI uri = new URI(url);
            return HOST.equalsIgnoreCase(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public List<CrawledPage> crawl(String url) {
        log.info("开始抓取商务厅栏目首页: {}", url);
        Map<String, LinkCandidate> candidates = new LinkedHashMap<>();
        Set<String> visitedColumns = new HashSet<>();

        collectCandidatesRecursively(url, candidates, visitedColumns);

        log.info("递归栏目候选链接提取完成: sourceUrl={} | candidateCount={} | visitedColumns={} | topLimit={}",
                url,
                candidates.size(),
                visitedColumns.size(),
                MAX_TOTAL_CANDIDATES);
        return candidates.values().stream()
                .sorted(Comparator.comparingInt(LinkCandidate::score).reversed())
                .limit(MAX_TOTAL_CANDIDATES)
                .map(candidate -> crawlCandidate(candidate, url))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private void collectCandidatesRecursively(String pageUrl, Map<String, LinkCandidate> candidates, Set<String> visitedColumns) {
        if (candidates.size() >= MAX_TOTAL_CANDIDATES) {
            return;
        }

        org.jsoup.nodes.Document doc = fetchHtmlDocument(pageUrl);
        List<String> columnUrls = discoverColumnUrls(doc, pageUrl);

        if (columnUrls.isEmpty()) {
            collectColumnPageCandidates(pageUrl, doc, candidates);
            return;
        }

        for (String columnUrl : columnUrls) {
            if (!visitedColumns.add(columnUrl)) {
                continue;
            }
            collectColumnPageCandidates(columnUrl, fetchHtmlDocument(columnUrl), candidates);
            if (candidates.size() >= MAX_TOTAL_CANDIDATES) {
                return;
            }
        }
    }

    private List<String> discoverColumnUrls(org.jsoup.nodes.Document doc, String baseUrl) {
        LinkedHashMap<String, String> columnUrls = new LinkedHashMap<>();
        for (Element link : doc.select(".c_n_column_li > a.custom-link[href], .c_n_policy_ul a.custom-link[href]")) {
            String href = link.absUrl("href").trim();
            if (isColumnUrl(href)) {
                columnUrls.putIfAbsent(href, href);
            }
        }

        if (columnUrls.isEmpty() && isColumnUrl(baseUrl)) {
            columnUrls.put(baseUrl, baseUrl);
        }

        return new ArrayList<>(columnUrls.values());
    }

    private void collectColumnPageCandidates(String columnUrl, org.jsoup.nodes.Document doc, Map<String, LinkCandidate> candidates) {
        ColumnPageConfig config = extractColumnPageConfig(doc, columnUrl);
        if (config == null) {
            log.info("栏目页缺少 jpage 配置，回退为静态链接抓取: {}", columnUrl);
            mergeCandidates(candidates, extractCandidates(doc, columnUrl));
            return;
        }

        log.info("开始递归抓取栏目页: columnUrl={} | columnId={} | totalRecord={} | perPage={}",
                columnUrl,
                config.columnId(),
                config.totalRecord(),
                config.perPage());

        mergeCandidates(candidates, extractCandidatesFromDataStore(config.initialDataStore(), columnUrl));

        int maxRecords = Math.min(config.totalRecord(), MAX_RECORDS_PER_COLUMN);
        for (int startRecord = config.perPage() + 1; startRecord <= maxRecords; startRecord += config.perPage()) {
            int endRecord = Math.min(maxRecords, startRecord + config.perPage() - 1);
            String xml = fetchColumnPageXml(config, startRecord, endRecord);
            mergeCandidates(candidates, extractCandidatesFromDataStore(xml, columnUrl));
            if (candidates.size() >= MAX_TOTAL_CANDIDATES) {
                return;
            }
        }
    }

    private Map<String, LinkCandidate> extractCandidates(org.jsoup.nodes.Document doc, String baseUrl) {
        Map<String, LinkCandidate> candidates = new LinkedHashMap<>();
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.absUrl("href").trim();
            if (href.isBlank() || !isSupportedHost(href)) {
                continue;
            }
            String title = link.text().trim();
            int score = scoreLink(title, href);
            if (score <= 0) {
                continue;
            }
            candidates.compute(href, (key, existing) -> {
                if (existing == null || score > existing.score()) {
                    return new LinkCandidate(href, baseUrl, title, score, isAttachmentUrl(href));
                }
                return existing;
            });
        }
        return candidates;
    }

    private Map<String, LinkCandidate> extractCandidatesFromDataStore(String dataStoreXml, String sourcePage) {
        Map<String, LinkCandidate> candidates = new LinkedHashMap<>();
        if (dataStoreXml == null || dataStoreXml.isBlank()) {
            return candidates;
        }

        org.jsoup.nodes.Document xmlDoc = Jsoup.parse(dataStoreXml, "", org.jsoup.parser.Parser.xmlParser());
        for (Element record : xmlDoc.select("recordset > record")) {
            String htmlSnippet = record.text();
            if (htmlSnippet == null || htmlSnippet.isBlank()) {
                continue;
            }
            org.jsoup.nodes.Document snippetDoc = Jsoup.parseBodyFragment(htmlSnippet, sourcePage);
            for (Element link : snippetDoc.select("a[href]")) {
                String href = link.absUrl("href").trim();
                if (href.isBlank()) {
                    continue;
                }
                String title = link.text().replaceAll("\\s+", " ").trim();
                int score = scoreLink(title, href);
                if (score <= 0) {
                    continue;
                }
                candidates.compute(href, (key, existing) -> {
                    if (existing == null || score > existing.score()) {
                        return new LinkCandidate(href, sourcePage, title, score, isAttachmentUrl(href));
                    }
                    return existing;
                });
            }
        }
        return candidates;
    }

    private void mergeCandidates(Map<String, LinkCandidate> target, Map<String, LinkCandidate> additions) {
        for (Map.Entry<String, LinkCandidate> entry : additions.entrySet()) {
            if (target.size() >= MAX_TOTAL_CANDIDATES) {
                return;
            }
            target.compute(entry.getKey(), (key, existing) -> {
                LinkCandidate candidate = entry.getValue();
                if (existing == null || candidate.score() > existing.score()) {
                    return candidate;
                }
                return existing;
            });
        }
    }

    private ColumnPageConfig extractColumnPageConfig(org.jsoup.nodes.Document doc, String columnUrl) {
        String html = doc.outerHtml();
        Matcher paramMatcher = PARAM_PATTERN.matcher(html);
        Matcher totalRecordMatcher = TOTAL_RECORD_PATTERN.matcher(html);
        Matcher perPageMatcher = PER_PAGE_PATTERN.matcher(html);
        Matcher proxyUrlMatcher = PROXY_URL_PATTERN.matcher(html);

        if (!paramMatcher.find() || !totalRecordMatcher.find() || !perPageMatcher.find() || !proxyUrlMatcher.find()) {
            return null;
        }

        Element dataStoreScript = doc.selectFirst("div#" + paramMatcher.group(7) + " > script");
        String dataStore = dataStoreScript != null ? dataStoreScript.html() : "";

        return new ColumnPageConfig(
                columnUrl,
                Integer.parseInt(totalRecordMatcher.group(1)),
                Integer.parseInt(perPageMatcher.group(1)),
                paramMatcher.group(7),
                Integer.parseInt(paramMatcher.group(5)),
                buildAbsoluteUrl(columnUrl, proxyUrlMatcher.group(1)),
                Integer.parseInt(paramMatcher.group(2)),
                Integer.parseInt(paramMatcher.group(3)),
                paramMatcher.group(4),
                Integer.parseInt(paramMatcher.group(6)),
                URLDecoder.decode(paramMatcher.group(8), StandardCharsets.UTF_8),
                Integer.parseInt(paramMatcher.group(9)),
                dataStore
        );
    }

    private String fetchColumnPageXml(ColumnPageConfig config, int startRecord, int endRecord) {
        try {
            String requestUrl = String.format(Locale.ROOT,
                    "%s?startrecord=%d&endrecord=%d&perpage=%d&unitid=%s&webid=%d&path=%s&webname=%s&col=%d&columnid=%d&sourceContentType=%d&permissiontype=%d",
                    config.proxyUrl(),
                    startRecord,
                    endRecord,
                    config.perPage(),
                    config.unitId(),
                    config.webId(),
                    config.path(),
                    java.net.URLEncoder.encode(config.webName(), StandardCharsets.UTF_8),
                    config.col(),
                    config.columnId(),
                    config.sourceContentType(),
                    config.permissionType());

            HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(buildColumnRequestBody(config)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalArgumentException("栏目分页抓取失败: " + config.columnUrl());
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalArgumentException("栏目分页抓取失败: " + config.columnUrl(), e);
        }
    }

    private String buildColumnRequestBody(ColumnPageConfig config) {
        return "col=" + config.col()
                + "&webid=" + config.webId()
                + "&path=" + java.net.URLEncoder.encode(config.path(), StandardCharsets.UTF_8)
                + "&columnid=" + config.columnId()
                + "&sourceContentType=" + config.sourceContentType()
                + "&unitid=" + java.net.URLEncoder.encode(config.unitId(), StandardCharsets.UTF_8)
                + "&webname=" + java.net.URLEncoder.encode(config.webName(), StandardCharsets.UTF_8)
                + "&permissiontype=" + config.permissionType();
    }

    private Optional<CrawledPage> crawlCandidate(LinkCandidate candidate, String entryUrl) {
        try {
            log.info("开始抓取候选链接: url={} | score={} | attachment={}", candidate.url(), candidate.score(), candidate.attachment());
            if (candidate.attachment()) {
                CrawledAttachment attachment = fetchAttachment(candidate.url());
                if (attachment == null || attachment.parsedText() == null || attachment.parsedText().isBlank()) {
                    log.info("附件候选跳过，未提取到正文: {}", candidate.url());
                    return Optional.empty();
                }
                return Optional.of(CrawledPage.builder()
                        .sourceUrl(candidate.url())
                        .sourcePage(entryUrl)
                        .sourceSite(SOURCE_SITE)
                        .itemType(UrlImportItemType.ATTACHMENT)
                        .title(candidate.title().isBlank() ? attachment.fileName() : candidate.title())
                        .publishDate(null)
                        .rawHtml("")
                        .extractedText(attachment.parsedText())
                        .attachments(List.of(attachment))
                        .build());
            }

            org.jsoup.nodes.Document detailDoc = fetchHtmlDocument(candidate.url());
            String title = extractTitle(detailDoc, candidate.title());
            LocalDate publishDate = extractPublishDate(detailDoc.text()).orElse(null);
            String articleText = extractArticleText(detailDoc);
            List<CrawledAttachment> attachments = extractAttachments(detailDoc, candidate.url());
            String bestText = chooseBestText(articleText, attachments);
                log.info("候选链接抓取完成: url={} | articleLength={} | attachments={}",
                    candidate.url(),
                    bestText != null ? bestText.length() : 0,
                    attachments.size());
            return Optional.of(CrawledPage.builder()
                    .sourceUrl(candidate.url())
                    .sourcePage(entryUrl)
                    .sourceSite(SOURCE_SITE)
                    .itemType(UrlImportItemType.ARTICLE)
                    .title(title)
                    .publishDate(publishDate)
                    .rawHtml(detailDoc.outerHtml())
                    .extractedText(bestText)
                    .attachments(attachments)
                    .build());
        } catch (Exception e) {
            log.warn("抓取候选内容失败: {}", candidate.url(), e);
            return Optional.empty();
        }
    }

    private String chooseBestText(String articleText, List<CrawledAttachment> attachments) {
        String normalizedArticle = articleText == null ? "" : articleText.trim();
        if (normalizedArticle.length() >= 400) {
            return normalizedArticle;
        }
        for (CrawledAttachment attachment : attachments) {
            String parsedText = attachment.parsedText();
            if (parsedText != null && parsedText.length() > normalizedArticle.length()) {
                normalizedArticle = parsedText;
            }
        }
        return normalizedArticle;
    }

    private List<CrawledAttachment> extractAttachments(org.jsoup.nodes.Document detailDoc, String pageUrl) {
        Map<String, CrawledAttachment> attachments = new LinkedHashMap<>();
        for (Element link : detailDoc.select("a[href]")) {
            String href = link.absUrl("href").trim();
            if (!isAttachmentUrl(href)) {
                continue;
            }
            try {
                CrawledAttachment attachment = fetchAttachment(href);
                if (attachment != null) {
                    attachments.putIfAbsent(href, attachment);
                }
            } catch (Exception e) {
                log.warn("下载附件失败: {} | page={}", href, pageUrl, e);
            }
        }
        return new ArrayList<>(attachments.values());
    }

    private CrawledAttachment fetchAttachment(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                return null;
            }
            String fileName = extractFileName(url);
            String parsedText = parseAttachmentText(response.body(), fileName);
            return CrawledAttachment.builder()
                    .attachmentUrl(url)
                    .fileName(fileName)
                    .fileType(detectFileType(fileName))
                    .bytes(response.body())
                    .parsedText(parsedText)
                    .build();
        } catch (Exception e) {
            log.warn("获取附件失败: {}", url, e);
            return null;
        }
    }

    private String parseAttachmentText(byte[] bytes, String fileName) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        // Website import must stay responsive; skip slow OCR fallback during crawling.
        List<Document> docs = documentLoaderService.loadDocumentFromResource(resource, fileName, false);
        return docs.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n\n" + right)
                .trim();
    }

    private org.jsoup.nodes.Document fetchHtmlDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(20000)
                    .maxBodySize(0)
                    .get();
        } catch (IOException e) {
            throw new IllegalArgumentException("抓取网页失败: " + url);
        }
    }

    private String extractTitle(org.jsoup.nodes.Document detailDoc, String fallback) {
        for (String selector : List.of("h1", ".article-title", ".title", ".arti_title")) {
            Element element = detailDoc.selectFirst(selector);
            if (element != null && !element.text().isBlank()) {
                return element.text().trim();
            }
        }
        return fallback == null || fallback.isBlank() ? detailDoc.title().trim() : fallback;
    }

    private String extractArticleText(org.jsoup.nodes.Document detailDoc) {
        for (String selector : List.of("div.TRS_Editor", "#zoom", "div.article", "div.content", "div.news_content", "div.article-content")) {
            Element root = detailDoc.selectFirst(selector);
            if (root != null) {
                String text = root.text().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return detailDoc.select("p").eachText().stream()
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n\n" + right)
                .trim();
    }

    private Optional<LocalDate> extractPublishDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String normalized = String.format(Locale.ROOT, "%s-%02d-%02d",
                matcher.group(1),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
        return Optional.of(LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE));
    }

    private int scoreLink(String title, String url) {
        String haystack = (title + " " + url).toLowerCase(Locale.ROOT);
        int score = 0;
        if (url.contains("/art/")) {
            score += 20;
        }
        if (isAttachmentUrl(url)) {
            score += 25;
        }
        for (String keyword : POLICY_KEYWORDS) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 12;
            }
        }
        for (String keyword : LOW_PRIORITY_KEYWORDS) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                score -= 8;
            }
        }
        return score;
    }

    private boolean isSupportedHost(String url) {
        try {
            URI uri = new URI(url);
            return HOST.equalsIgnoreCase(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isAttachmentUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return ATTACHMENT_EXTENSIONS.stream().anyMatch(ext -> lower.endsWith("." + ext));
    }

    private boolean isColumnUrl(String url) {
        return isSupportedHost(url) && url.contains("/col/col") && url.endsWith("/index.html");
    }

    private String buildAbsoluteUrl(String baseUrl, String maybeRelativeUrl) {
        return URI.create(baseUrl).resolve(maybeRelativeUrl).toString();
    }

    private String extractFileName(String url) {
        String path = URI.create(url).getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return "document.txt";
    }

    private String detectFileType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "application/octet-stream";
    }

    private record LinkCandidate(String url, String sourcePage, String title, int score, boolean attachment) {
    }

    private record ColumnPageConfig(
            String columnUrl,
            int totalRecord,
            int perPage,
            String unitId,
            int columnId,
            String proxyUrl,
            int col,
            int webId,
            String path,
            int sourceContentType,
            String webName,
            int permissionType,
            String initialDataStore) {
    }
}