package com.shandong.policyagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

/**
 * 运行时向量存储，实现 Spring AI VectorStore 接口。
 * 检索通过 {@link HybridRetrievalPipeline} 走完整混合检索管线，写入/删除直接走底层 MultiVectorStoreService。
 */
@Slf4j
@Component("runtimeRagVectorStore")
@RequiredArgsConstructor
public class RuntimeRagVectorStore implements VectorStore {

    private final HybridRetrievalPipeline pipeline;
    private final MultiVectorStoreService multiVectorStoreService;
    private final RagConfig ragConfig;

    @Override
    public void add(List<Document> documents) {
        HybridRetrievalPipeline.RetrievalContext ctx = pipeline.resolveRetrievalContext();
        multiVectorStoreService.getVectorStore(ctx.embeddingModelId()).add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        HybridRetrievalPipeline.RetrievalContext ctx = pipeline.resolveRetrievalContext();
        multiVectorStoreService.getVectorStore(ctx.embeddingModelId()).delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        HybridRetrievalPipeline.RetrievalContext ctx = pipeline.resolveRetrievalContext();
        multiVectorStoreService.getVectorStore(ctx.embeddingModelId()).delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        HybridRetrievalPipeline.RetrievalContext ctx = pipeline.resolveRetrievalContext();
        log.debug("RAG 检索使用嵌入模型: {}", ctx.embeddingModelId());

        if (ctx.missing()) {
            log.warn("知识库范围配置的文件夹不存在，返回空召回结果 | folderId={}", ctx.folderId());
            return List.of();
        }

        List<Document> results = pipeline.retrieve(
                request.getQuery(),
                request.getTopK(),
                request.getSimilarityThreshold(),
                ctx
        );

        return sanitizeRetrievedDocuments(results);
    }

    public HybridRetrievalPipeline.RetrievalContext resolveRetrievalContext() {
        return pipeline.resolveRetrievalContext();
    }

    private List<Document> sanitizeRetrievedDocuments(List<Document> documents) {
        int promptMaxDocChars = Math.max(200, ragConfig.getRetrieval().getPromptMaxDocChars());
        return documents.stream()
                .map(document -> truncateForPrompt(document, promptMaxDocChars))
                .toList();
    }

    /**
     * 智能截断文档文本，适应 prompt 长度限制。
     * 优先在中文句号（。！？；）、换行等自然边界处截断；
     * 在 promptMaxDocChars 限制内找最后一个合法边界，找不到时回退为硬截断并添加省略号。
     * 始终保留 truncatedForPrompt 元数据标记，供下游跟踪。
     */
    private Document truncateForPrompt(Document source, int promptMaxDocChars) {
        String text = source.getText();
        if (text == null || text.length() <= promptMaxDocChars) {
            return source;
        }

        HashMap<String, Object> metadata = source.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(source.getMetadata());
        metadata.put("truncatedForPrompt", true);
        metadata.put("promptChars", promptMaxDocChars);
        metadata.put("originalChars", text.length());

        // 在 promptMaxDocChars 范围内查找最后一个合适的中文边界
        int effectiveLimit = Math.min(promptMaxDocChars, text.length());
        int boundary = findLastSentenceBoundary(text, effectiveLimit);

        String truncated;
        if (boundary > 0) {
            truncated = text.substring(0, boundary).trim();
            metadata.put("truncationMethod", "smart");
            metadata.put("truncatedChars", truncated.length());
        } else {
            // 未找到合适边界，回退硬截断并追加省略号
            int hardLimit = Math.max(1, promptMaxDocChars - 3);
            truncated = text.substring(0, Math.min(hardLimit, text.length())).trim() + "...";
            metadata.put("truncationMethod", "hard");
            metadata.put("truncatedChars", truncated.length());
        }

        return new Document(source.getId(), truncated, metadata);
    }

    /**
     * 在文本的 [0, effectiveLimit) 范围内，从后向前查找中文句子边界。
     * 优先级：中文句号/问号/感叹号/分号 > 换行 > 逗号/顿号。
     * 仅在有效范围的后 25% 区间内搜索，避免过度截断。
     *
     * @return 截断位置（包含边界字符），未找到时返回 -1
     */
    private int findLastSentenceBoundary(String text, int effectiveLimit) {
        int searchStart = Math.max(1, effectiveLimit * 3 / 4);

        // 第一优先级：强句子边界（。！？；\n）
        for (int i = effectiveLimit - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                return i + 1; // 包含边界字符
            }
        }

        // 第二优先级：弱句子边界（，、）
        for (int i = effectiveLimit - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '，' || c == '、') {
                return i + 1;
            }
        }

        return -1; // 未找到合适边界
    }
}
