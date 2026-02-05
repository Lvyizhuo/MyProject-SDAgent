package com.shandong.policyagent.rag;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final VectorStore vectorStore;
    private final DocumentLoaderService documentLoaderService;
    private final TextSplitterService textSplitterService;

    public void addDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            log.warn("没有文档需要添加到向量存储");
            return;
        }

        List<Document> splitDocs = textSplitterService.splitDocuments(documents);
        addDocumentsInBatches(splitDocs);
        log.info("成功添加 {} 个文档切片到向量存储", splitDocs.size());
    }

    public int loadAndStoreAllDocuments() {
        List<Document> documents = documentLoaderService.loadAllDefaultDocuments();
        if (documents.isEmpty()) {
            log.warn("未找到任何文档");
            return 0;
        }

        List<Document> splitDocs = textSplitterService.splitDocuments(documents);
        addDocumentsInBatches(splitDocs);
        log.info("ETL 完成: 加载并存储 {} 个文档切片", splitDocs.size());

        return splitDocs.size();
    }

    public int loadAndStoreFromDirectory(String directoryPath) {
        List<Document> documents = documentLoaderService.loadDocumentsFromDirectory(directoryPath);
        if (documents.isEmpty()) {
            log.warn("目录 {} 中未找到文档", directoryPath);
            return 0;
        }

        List<Document> splitDocs = textSplitterService.splitDocuments(documents);
        addDocumentsInBatches(splitDocs);
        log.info("从目录 {} 加载并存储 {} 个文档切片", directoryPath, splitDocs.size());

        return splitDocs.size();
    }

    /**
     * 分批添加文档到向量存储，避免 DashScope embedding API 批量大小限制（最大10）
     */
    private void addDocumentsInBatches(List<Document> documents) {
        List<List<Document>> batches = partitionList(documents, EMBEDDING_BATCH_SIZE);
        log.info("将 {} 个文档分成 {} 批进行嵌入处理", documents.size(), batches.size());

        for (int i = 0; i < batches.size(); i++) {
            List<Document> batch = batches.get(i);
            vectorStore.add(batch);
            log.debug("已处理第 {}/{} 批，包含 {} 个文档", i + 1, batches.size(), batch.size());
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    public void deleteDocuments(List<String> documentIds) {
        vectorStore.delete(documentIds);
        log.info("已删除 {} 个文档", documentIds.size());
    }
}
