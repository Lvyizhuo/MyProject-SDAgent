package com.shandong.policyagent.rag;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextSplitterService {

    private final RagConfig ragConfig;

    public List<Document> splitDocuments(List<Document> documents) {
        RagConfig.Chunking chunkConfig = ragConfig.getChunking();

        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkConfig.getDefaultChunkSize(),
                chunkConfig.getMinChunkSizeChars(),
                chunkConfig.getMinChunkLengthToEmbed(),
                chunkConfig.getChunkOverlap(),
                chunkConfig.isKeepSeparator()
        );

        List<Document> splitDocs = splitter.apply(documents);
        log.info("文档切片完成: 原始 {} 个文档 -> {} 个切片", documents.size(), splitDocs.size());

        return splitDocs;
    }

    public List<Document> splitDocument(Document document) {
        return splitDocuments(List.of(document));
    }
}
