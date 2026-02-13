package com.shandong.policyagent.rag;

import java.util.List;
import java.util.ArrayList;

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

        List<Document> passthroughDocs = new ArrayList<>();
        List<Document> toSplitDocs = new ArrayList<>();

        for (Document document : documents) {
            String text = document.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (text.length() <= chunkConfig.getNoSplitMaxChars()) {
                passthroughDocs.add(document);
            } else {
                toSplitDocs.add(document);
            }
        }

        List<Document> splitDocs = new ArrayList<>(passthroughDocs);
        if (!toSplitDocs.isEmpty()) {
            splitDocs.addAll(splitter.apply(toSplitDocs));
        }
        log.info(
                "文档切片完成: 原始 {} 个文档 -> {} 个切片 (短文直入 {}，长文切分 {})",
                documents.size(),
                splitDocs.size(),
                passthroughDocs.size(),
                toSplitDocs.size()
        );

        return splitDocs;
    }

    public List<Document> splitDocument(Document document) {
        return splitDocuments(List.of(document));
    }
}
