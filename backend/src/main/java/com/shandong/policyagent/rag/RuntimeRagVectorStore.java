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
        return new Document(source.getId(), text.substring(0, promptMaxDocChars).trim(), metadata);
    }
}
