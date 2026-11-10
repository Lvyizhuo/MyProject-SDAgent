package com.shandong.policyagent.rag;

import com.shandong.policyagent.agent.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.support.StageTimer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 混合检索管线：统合向量召回 + 关键词召回 → RRF 融合 → Rerank 重排序 → 阈值过滤 → Parent 扩展 → 年份排序。
 * 提供单一入口，供 RuntimeRagVectorStore 和 RagRetrievalService 共同使用，消除循环依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrievalPipeline {

    private static final int DEFAULT_RRF_K = 60;
    private static final double VECTOR_RRF_WEIGHT = 0.65;
    private static final double KEYWORD_RRF_WEIGHT = 0.35;
    /**
     * Parent 扩展半径：命中的 child chunk 向前后各取 N 个 sibling chunk 拼成完整段落。
     * 1→0：单轮 RAG 上下文从 ~4700 token 降到 ~3100（top-k 2 + 不扩展），
     * 代价是上下文少了相邻段落的衔接，命中片段本身的完整度不受影响。
     */
    private static final int PARENT_EXPAND_RADIUS = 0;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final KnowledgeFolderRepository knowledgeFolderRepository;
    private final KnowledgeService knowledgeService;
    private final MultiVectorStoreService multiVectorStoreService;
    private final EmbeddingService embeddingService;
    private final DashScopeRerankService rerankService;
    private final RagConfig ragConfig;
    private final RetrievalResultCache retrievalResultCache;
    private final SemanticRetrievalCache semanticRetrievalCache;
    private final com.shandong.policyagent.support.ChatMetrics chatMetrics;

    /**
     * 检索并行执行器：向量召回与关键词召回共用。
     * 独立小池，避免与对话线程池（chatAsyncTaskExecutor）争抢 CPU。
     */
    private final Executor retrievalExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "rag-retrieval-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    /**
     * 等待并行任务结果；异常时降级为空列表（关键词召回失败不影响主链路）。
     */
    private List<Document> joinQuietly(CompletableFuture<List<Document>> future) {
        try {
            return future.join();
        } catch (Exception e) {
            log.warn("检索子任务异常 | error={}", e.getMessage());
            return List.of();
        }
    }

    // ==================== 检索上下文解析（从 RuntimeRagVectorStore 迁入） ====================

    public RetrievalContext resolveRetrievalContext() {
        ScopeResolution scope = resolveKnowledgeBaseScope();
        String embeddingModelId = scope.enabled() && scope.embeddingModelId() != null && !scope.embeddingModelId().isBlank()
                ? scope.embeddingModelId()
                : resolveFallbackEmbeddingModelId();
        String vectorTableName = scope.enabled() && scope.vectorTableName() != null && !scope.vectorTableName().isBlank()
                ? scope.vectorTableName()
                : embeddingService.getModelConfig(embeddingModelId).getVectorTable();

        if (scope.missing()) {
            return new RetrievalContext(embeddingModelId, vectorTableName, scope.folderId(), null, null, null, true);
        }

        String folderPath = scope.enabled() ? scope.folderPath() : null;
        Long folderId = scope.enabled() ? scope.folderId() : null;
        Long rerankModelId = scope.enabled() ? scope.rerankModelId() : null;
        String rerankModelName = scope.enabled() ? scope.rerankModelName() : null;
        return new RetrievalContext(embeddingModelId, vectorTableName, folderId, folderPath, rerankModelId, rerankModelName, false);
    }

    public record RetrievalContext(String embeddingModelId,
                                   String vectorTableName,
                                   Long folderId,
                                   String folderPath,
                                   Long rerankModelId,
                                   String rerankModelName,
                                   boolean missing) {
    }

    // ==================== 主检索入口 ====================

    /**
     * 完整混合检索管线。供 RuntimeRagVectorStore 直接调用。
     *
     * @param query               用户查询
     * @param topK                最终返回文档数
     * @param similarityThreshold 向量召回相似度阈值（0-1），用于 SQL 预过滤
     * @param context             已解析的检索上下文
     * @return 排序后的最终文档列表
     */
    public List<Document> retrieve(String query, int topK, double similarityThreshold, RetrievalContext context) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (context == null || context.missing()) {
            log.warn("知识库范围配置异常，返回空召回结果 | folderId={}", context == null ? null : context.folderId());
            return List.of();
        }

        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();
        int finalTopK = Math.max(1, topK);
        int candidateTopK = Math.max(finalTopK, retrievalConfig.getCandidateTopK());
        double effectiveThreshold = similarityThreshold > 0 ? similarityThreshold : 0;

        StageTimer timer = new StageTimer("ragRetrieve", chatMetrics.observer("ragRetrieve"));

        // 检索 query 是被 ExecutionPromptBuilder 增强过的完整用户消息（2000~4000 字符），
        // 直接拿它做嵌入与缓存键会被模板噪声主导。这里先抽出真实用户问题：
        //   · 嵌入只用真实问题 → 检索更准、嵌入更快（实测 140ms vs 2700ms 波动）
        //   · 缓存键/语义注册只用真实问题 → 改写问法才能命中语义缓存
        String rawQuery = RawQueryExtractor.extract(query);
        String cacheQuery = RawQueryExtractor.extractForCache(query, 200);

        // 检索结果级缓存：优先读缓存，命中后直接返回（跳过管线）
        if (retrievalConfig.getCacheTtlMinutes() > 0) {
            String cacheKey = retrievalResultCache.buildKey(
                    cacheQuery, context.embeddingModelId(), context.folderPath(), finalTopK, effectiveThreshold);
            var cached = timer.time("cacheGet", () -> retrievalResultCache.get(cacheKey));
            if (cached.isPresent()) {
                chatMetrics.recordCache("hit");
                timer.logSummary("ragRetrieve", "cacheHit");
                return cached.get();
            }

            // 语义化缓存：政策咨询的措辞差异极大，纯文本键命中率≈0。
            // 这里用「与向量召回共用」的查询向量做近似匹配，命中即复用当时的检索结果。
            float[] queryEmbedding = timer.time("embedQuery", () -> embedQuery(context.embeddingModelId(), rawQuery));
            if (queryEmbedding != null) {
                var similarKey = timer.time("semanticCacheGet",
                        () -> semanticRetrievalCache.findSimilarKey(queryEmbedding));
                if (similarKey.isPresent()) {
                    var semanticCached = timer.time("semanticCacheLoad",
                            () -> retrievalResultCache.get(similarKey.get()));
                    if (semanticCached.isPresent()) {
                        chatMetrics.recordCache("semantic_hit");
                        timer.logSummary("ragRetrieve", "semanticCacheHit");
                        return semanticCached.get();
                    }
                }
                chatMetrics.recordCache("semantic_miss");
            }

            // 缓存未命中，执行管线后将结果回写缓存
            List<Document> results = executePipeline(rawQuery, context, retrievalConfig, finalTopK, candidateTopK,
                    effectiveThreshold, timer, queryEmbedding);
            timer.time("cachePut", () -> retrievalResultCache.put(cacheKey, results));
            if (queryEmbedding != null) {
                timer.time("semanticCacheRegister",
                        () -> semanticRetrievalCache.register(cacheQuery, queryEmbedding, cacheKey));
            }
            chatMetrics.recordCache("miss");
            timer.logSummary("ragRetrieve", "cacheMiss");
            return results;
        }

        // 缓存禁用时直接执行管线
        return executePipeline(query, context, retrievalConfig, finalTopK, candidateTopK, effectiveThreshold, timer, null);
    }

    /**
     * 计算查询向量（供语义缓存匹配与向量召回共用，避免重复调用嵌入模型）。
     */
    private float[] embedQuery(String embeddingModelId, String query) {
        try {
            List<float[]> embeddings = embeddingService.embedTexts(embeddingModelId, List.of(query));
            return embeddings.isEmpty() ? null : embeddings.get(0);
        } catch (Exception e) {
            log.debug("计算查询向量失败，跳过语义化缓存 | error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行完整检索管线（不含缓存逻辑），供 retrieve 缓存装饰层调用。
     */
    private List<Document> executePipeline(String query,
                                           RetrievalContext context,
                                           RagConfig.Retrieval retrievalConfig,
                                           int finalTopK,
                                           int candidateTopK,
                                           double effectiveThreshold,
                                           StageTimer timer,
                                           float[] precomputedEmbedding) {
        String expandedQuery = expandQueryForSynonyms(query);

        // 1. 向量召回 与 2. 关键词召回 互不依赖，并行执行
        //    （各自 1 次嵌入 + 1 次 pgvector 查询，实测向量 ~620ms、关键词 ~90ms，
        //      串行改为并行后 RAG 检索阶段约省 90ms）
        final float[] queryEmbedding = precomputedEmbedding;
        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(
                () -> {
                    if (queryEmbedding != null) {
                        return multiVectorStoreService.similaritySearch(
                                context.embeddingModelId(),
                                queryEmbedding,
                                candidateTopK,
                                context.folderPath(),
                                "child",
                                effectiveThreshold
                        );
                    }
                    return multiVectorStoreService.similaritySearch(
                            context.embeddingModelId(),
                            expandedQuery,
                            candidateTopK,
                            context.folderPath(),
                            "child",
                            effectiveThreshold
                    );
                }, retrievalExecutor);

        CompletableFuture<List<Document>> keywordFuture = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return multiVectorStoreService.keywordSearch(
                                context.embeddingModelId(),
                                expandedQuery,
                                candidateTopK,
                                context.folderPath(),
                                "child"
                        );
                    } catch (Exception e) {
                        log.warn("关键词检索异常，降级为纯向量检索 | query={} | error={}",
                                truncateQuery(query), e.getMessage());
                        return List.<Document>of();
                    }
                }, retrievalExecutor);

        List<Document> vectorCandidates = timer.time("vectorRecall", () -> joinQuietly(vectorFuture));

        if (vectorCandidates.isEmpty()) {
            vectorCandidates = timer.time("vectorRecallFallback",
                    () -> multiVectorStoreService.similaritySearch(
                            context.embeddingModelId(),
                            expandedQuery,
                            candidateTopK,
                            context.folderPath(),
                            null,
                            effectiveThreshold
                    ));
        }

        List<Document> keywordCandidates = timer.time("keywordRecall", () -> joinQuietly(keywordFuture));

        // 3. RRF 融合
        List<Document> vectorForFuse = vectorCandidates;
        List<Document> keywordForFuse = keywordCandidates;
        List<Document> fusedCandidates = timer.time("rrfFuse",
                () -> fuseByRrf(vectorForFuse, keywordForFuse, candidateTopK));

        // 4. Rerank 重排序（内置回退纯向量）
        List<Document> reranked = timer.time("rerank",
                () -> rerankService.rerank(
                        expandedQuery,
                        fusedCandidates,
                        finalTopK,
                        context.rerankModelId(),
                        context.rerankModelName()
                ));

        // 5. Rerank 分数阈值过滤
        List<Document> thresholdFiltered = timer.time("thresholdFilter",
                () -> applyRerankThreshold(
                        reranked,
                        knowledgeService.resolveRerankScoreThreshold(),
                        finalTopK
                ));

        // 6. Parent-child 扩展
        List<Document> expanded = timer.time("parentExpand",
                () -> expandChildHitsToParent(context.vectorTableName(), thresholdFiltered, finalTopK));

        // 7. 政策年份偏好排序
        List<Document> ordered = timer.time("yearSort",
                () -> preferLatestPolicyDocuments(expanded, finalTopK));

        log.debug("检索完成 | query={} | vector={} | keyword={} | fused={} | reranked={} | expanded={}",
                truncateQuery(query),
                vectorCandidates.size(),
                keywordCandidates.size(),
                fusedCandidates.size(),
                thresholdFiltered.size(),
                ordered.size());

        return ordered;
    }

    // ==================== RRF 融合 ====================

    private List<Document> fuseByRrf(List<Document> vectorCandidates,
                                     List<Document> keywordCandidates,
                                     int limit) {
        Map<String, FusedCandidate> fused = new LinkedHashMap<>();

        mergeWithRrf(fused, vectorCandidates, VECTOR_RRF_WEIGHT);
        mergeWithRrf(fused, keywordCandidates, KEYWORD_RRF_WEIGHT);

        if (fused.isEmpty()) {
            return List.of();
        }

        List<FusedCandidate> sorted = fused.values().stream()
                .sorted(Comparator
                        .comparingDouble(FusedCandidate::fusedScore)
                        .reversed()
                        .thenComparingInt(FusedCandidate::bestRank))
                .toList();

        List<Document> result = new ArrayList<>();
        int maxSize = Math.max(1, limit);
        for (int i = 0; i < sorted.size() && result.size() < maxSize; i++) {
            FusedCandidate candidate = sorted.get(i);
            Map<String, Object> metadata = candidate.document().getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(candidate.document().getMetadata());
            metadata.put("hybridFusedScore", candidate.fusedScore());
            result.add(new Document(candidate.document().getId(), candidate.document().getText(), metadata));
        }

        return result;
    }

    private void mergeWithRrf(Map<String, FusedCandidate> fused,
                              List<Document> candidates,
                              double weight) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (int rank = 0; rank < candidates.size(); rank++) {
            Document document = candidates.get(rank);
            if (document == null || document.getId() == null || document.getId().isBlank()) {
                continue;
            }

            final int rankPosition = rank;
            double rrfScore = weight / (DEFAULT_RRF_K + rankPosition + 1.0);
            fused.compute(document.getId(), (id, existing) -> {
                if (existing == null) {
                    return new FusedCandidate(document, rrfScore, rankPosition);
                }
                return new FusedCandidate(
                        existing.document(),
                        existing.fusedScore() + rrfScore,
                        Math.min(existing.bestRank(), rankPosition)
                );
            });
        }
    }

    // ==================== Rerank 阈值过滤 ====================

    private List<Document> applyRerankThreshold(List<Document> reranked,
                                                double threshold,
                                                int topK) {
        if (reranked == null || reranked.isEmpty()) {
            return List.of();
        }

        List<Document> scoredDocs = reranked.stream()
                .filter(this::hasUsableScore)
                .toList();
        if (scoredDocs.isEmpty()) {
            return shrink(reranked, topK);
        }

        List<Document> passed = scoredDocs.stream()
                .filter(document -> extractScore(document) >= threshold)
                .toList();
        if (!passed.isEmpty()) {
            return shrink(passed, topK);
        }
        return shrink(scoredDocs, topK);
    }

    private boolean hasUsableScore(Document document) {
        return extractScore(document) > 0.0D;
    }

    private double extractScore(Document document) {
        if (document == null || document.getMetadata() == null) {
            return 0.0D;
        }
        Object score = document.getMetadata().get("rerankScore");
        if (score == null) {
            score = document.getMetadata().get("score");
        }
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        if (score != null) {
            try {
                return Double.parseDouble(String.valueOf(score));
            } catch (Exception ignored) {
                return 0.0D;
            }
        }
        return 0.0D;
    }

    // ==================== Parent-child 扩展 ====================

    private List<Document> expandChildHitsToParent(String vectorTableName,
                                                    List<Document> reranked,
                                                    int topK) {
        if (reranked == null || reranked.isEmpty()) {
            return List.of();
        }

        List<String> parentChunkIds = reranked.stream()
                .map(this::extractParentChunkId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        Map<String, Document> parentDocuments = multiVectorStoreService.loadDocumentsByIds(vectorTableName, parentChunkIds);
        Map<String, Document> finalDocs = new LinkedHashMap<>();

        for (Document candidate : reranked) {
            if (candidate == null) {
                continue;
            }

            String chunkLevel = valueOf(candidate.getMetadata(), "chunkLevel").toLowerCase(Locale.ROOT);
            if (!"child".equals(chunkLevel)) {
                finalDocs.putIfAbsent(candidate.getId(), candidate);
                continue;
            }

            String parentChunkId = extractParentChunkId(candidate);
            Document parent = parentDocuments.get(parentChunkId);
            if (parent == null) {
                finalDocs.putIfAbsent(candidate.getId(), candidate);
                continue;
            }

            Map<String, Object> parentMetadata = parent.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(parent.getMetadata());
            parentMetadata.put("matchedChildChunkId", candidate.getId());

            Object score = candidate.getMetadata() == null ? null : candidate.getMetadata().get("rerankScore");
            if (score == null && candidate.getMetadata() != null) {
                score = candidate.getMetadata().get("score");
            }
            if (score != null) {
                parentMetadata.put("rerankScore", score);
                parentMetadata.put("score", score);
            }

            String expandedText = expandParentWithNeighbors(vectorTableName, parentMetadata, parent.getText());
            finalDocs.putIfAbsent(parent.getId(), new Document(parent.getId(), expandedText, parentMetadata));
            if (finalDocs.size() >= Math.max(1, topK)) {
                break;
            }
        }

        return finalDocs.values().stream().limit(Math.max(1, topK)).toList();
    }

    private String extractParentChunkId(Document document) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        Object value = document.getMetadata().get("parentChunkId");
        if (value == null) {
            return null;
        }
        String id = String.valueOf(value).trim();
        return id.isBlank() ? null : id;
    }

    private String valueOf(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return "";
        }
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String expandParentWithNeighbors(String vectorTableName,
                                              Map<String, Object> parentMetadata,
                                              String fallbackText) {
        Long documentId = parseLongValue(parentMetadata == null ? null : parentMetadata.get("knowledgeDocumentId"));
        Integer parentChunkIndex = parseIntValue(parentMetadata == null ? null : parentMetadata.get("parentChunkIndex"));
        if (documentId == null || parentChunkIndex == null) {
            return fallbackText;
        }

        List<Document> parents = multiVectorStoreService.loadParentChunkWindow(
                vectorTableName,
                documentId,
                parentChunkIndex,
                PARENT_EXPAND_RADIUS
        );
        if (parents.isEmpty()) {
            return fallbackText;
        }
        return parents.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(fallbackText);
    }

    private Integer parseIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseLongValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    // ==================== 同义词扩展 ====================

    private String expandQueryForSynonyms(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        StringBuilder expanded = new StringBuilder(query.trim());

        boolean hasNotebook = normalized.contains("笔记本") || normalized.contains("笔记本电脑");
        boolean hasComputer = normalized.contains("电脑");
        if (hasNotebook && !hasComputer) {
            expanded.append(" 电脑");
        } else if (hasComputer && !hasNotebook) {
            expanded.append(" 笔记本");
        }

        if (normalized.contains("macbook") && !hasNotebook) {
            expanded.append(" 笔记本电脑");
        }

        return expanded.toString();
    }

    // ==================== 政策年份偏好排序 ====================

    private List<Document> preferLatestPolicyDocuments(List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<RankedDocument> ranked = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            ranked.add(new RankedDocument(doc, extractDocumentYear(doc), extractScore(doc), i));
        }

        ranked.sort(Comparator
                .comparingInt(RankedDocument::year).reversed()
                .thenComparingDouble(RankedDocument::score).reversed()
                .thenComparingInt(RankedDocument::index));

        return ranked.stream()
                .limit(Math.max(1, topK))
                .map(RankedDocument::document)
                .toList();
    }

    private int extractDocumentYear(Document document) {
        if (document == null || document.getMetadata() == null) {
            return 0;
        }
        Map<String, Object> metadata = document.getMetadata();
        Integer year = parseYearValue(metadata.get("publishYear"));
        if (year != null) {
            return year;
        }
        year = parseYearValue(metadata.get("year"));
        if (year != null) {
            return year;
        }
        Object publishDate = metadata.get("publishDate");
        if (publishDate != null) {
            Integer parsed = parseYearFromDate(String.valueOf(publishDate));
            if (parsed != null) {
                return parsed;
            }
        }
        Object title = metadata.get("title");
        Integer fromTitle = parseYearFromText(title == null ? "" : String.valueOf(title));
        return fromTitle == null ? 0 : fromTitle;
    }

    private Integer parseYearValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int year = number.intValue();
            return year >= 2020 && year <= 2099 ? year : null;
        }
        try {
            int year = Integer.parseInt(String.valueOf(value));
            return year >= 2020 && year <= 2099 ? year : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseYearFromDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim()).getYear();
        } catch (DateTimeParseException ignored) {
            return parseYearFromText(text);
        }
    }

    private Integer parseYearFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(text);
        Integer latest = null;
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year < 2020 || year > 2099) {
                continue;
            }
            if (latest == null || year > latest) {
                latest = year;
            }
        }
        return latest;
    }

    // ==================== 工具方法 ====================

    private String truncateQuery(String query) {
        return query.length() > 50 ? query.substring(0, 50) + "..." : query;
    }

    private List<Document> shrink(List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        if (documents.size() <= limit) {
            return documents;
        }
        return documents.subList(0, limit);
    }

    // ==================== 内部解析逻辑（原 RuntimeRagVectorStore） ====================

    private ScopeResolution resolveKnowledgeBaseScope() {
        AgentConfig config = dynamicAgentConfigHolder.get();
        if (config == null || config.getKnowledgeBaseFolderId() == null) {
            return ScopeResolution.all();
        }

        Optional<KnowledgeFolder> folder = knowledgeFolderRepository.findById(config.getKnowledgeBaseFolderId());
        if (folder.isEmpty()) {
            return ScopeResolution.missing(config.getKnowledgeBaseFolderId());
        }

        KnowledgeFolder knowledgeBase = folder.get();
        return ScopeResolution.folder(
                knowledgeBase.getId(),
                knowledgeBase.getPath(),
                knowledgeBase.getEmbeddingModel(),
                knowledgeBase.getVectorTableName(),
                knowledgeBase.getRerankModelId(),
                knowledgeBase.getRerankModelName()
        );
    }

    private record ScopeResolution(Long folderId,
                                   String folderPath,
                                   String embeddingModelId,
                                   String vectorTableName,
                                   Long rerankModelId,
                                   String rerankModelName,
                                   boolean enabled,
                                   boolean missing) {
        private static ScopeResolution all() {
            return new ScopeResolution(null, null, null, null, null, null, false, false);
        }

        private static ScopeResolution folder(Long folderId,
                                              String folderPath,
                                              String embeddingModelId,
                                              String vectorTableName,
                                              Long rerankModelId,
                                              String rerankModelName) {
            return new ScopeResolution(folderId, folderPath, embeddingModelId, vectorTableName, rerankModelId, rerankModelName, true, false);
        }

        private static ScopeResolution missing(Long folderId) {
            return new ScopeResolution(folderId, null, null, null, null, null, false, true);
        }
    }

    private String resolveFallbackEmbeddingModelId() {
        return embeddingService.resolveDefaultModelId(knowledgeService.getConfig().getDefaultEmbeddingModel());
    }

    // ==================== 内部记录类型 ====================

    private record RankedDocument(Document document, int year, double score, int index) {
    }

    private record FusedCandidate(Document document, double fusedScore, int bestRank) {
    }
}
