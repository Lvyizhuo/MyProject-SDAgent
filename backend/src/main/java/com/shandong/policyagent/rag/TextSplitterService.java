package com.shandong.policyagent.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextSplitterService {
    private static final String SPLIT_STRATEGY = "SMART_SEMANTIC_V1";

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

        List<Document> splitDocs = new ArrayList<>();
        int passthroughCount = 0;
        int semanticSplitCount = 0;

        for (Document document : documents) {
            String text = document.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (text.length() <= chunkConfig.getNoSplitMaxChars()) {
                splitDocs.add(withMetadata(document, Map.of(
                        "splitStrategy", "NO_SPLIT_SHORT_DOC",
                        "chunkChars", text.length()
                )));
                passthroughCount++;
            } else {
                List<Document> semanticChunks = splitBySemantics(document, chunkConfig, splitter);
                splitDocs.addAll(semanticChunks);
                semanticSplitCount++;
            }
        }

        log.info(
                "文档智能切片完成: 原始 {} 个文档 -> {} 个切片 (短文直入 {}，语义切分 {})",
                documents.size(),
                splitDocs.size(),
                passthroughCount,
                semanticSplitCount
        );

        return splitDocs;
    }

    public List<Document> splitDocument(Document document) {
        return splitDocuments(List.of(document));
    }

    private List<Document> splitBySemantics(Document document,
                                            RagConfig.Chunking chunkConfig,
                                            TokenTextSplitter fallbackSplitter) {
        String normalizedText = normalizeText(document.getText());
        int targetChunkChars = Math.max(chunkConfig.getDefaultChunkSize(), chunkConfig.getMinChunkSizeChars());
        int overlapChars = Math.max(0, chunkConfig.getChunkOverlap());

        List<String> segments = splitToSegments(normalizedText);
        List<String> baseChunks = assembleChunks(segments, targetChunkChars, chunkConfig.getMinChunkSizeChars());

        if (baseChunks.isEmpty()) {
            return fallbackSplitter.apply(List.of(document));
        }

        List<String> overlappedChunks = applyOverlap(baseChunks, overlapChars);
        List<Document> result = new ArrayList<>();
        for (String chunk : overlappedChunks) {
            String cleanChunk = chunk == null ? "" : chunk.trim();
            if (cleanChunk.isBlank()) {
                continue;
            }
            result.add(withMetadata(document, Map.of(
                    "splitStrategy", SPLIT_STRATEGY,
                    "chunkChars", cleanChunk.length()
            ), cleanChunk));
        }

        return result.isEmpty() ? fallbackSplitter.apply(List.of(document)) : result;
    }

    private String normalizeText(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private List<String> splitToSegments(String text) {
        List<String> segments = new ArrayList<>();
        for (String paragraph : text.split("(?m)\\n\\s*\\n+")) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isBlank()) {
                continue;
            }
            segments.add(trimmedParagraph);
        }
        if (segments.isEmpty()) {
            segments.add(text);
        }
        return segments;
    }

    private List<String> assembleChunks(List<String> segments, int targetChunkChars, int minChunkChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String segment : segments) {
            if (segment.length() > targetChunkChars * 1.4) {
                List<String> sentenceChunks = splitLongSegmentBySentence(segment, targetChunkChars);
                for (String sentenceChunk : sentenceChunks) {
                    appendWithPacking(chunks, current, sentenceChunk, targetChunkChars);
                }
            } else {
                appendWithPacking(chunks, current, segment, targetChunkChars);
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        return mergeTinyChunks(chunks, minChunkChars);
    }

    private void appendWithPacking(List<String> chunks, StringBuilder current, String segment, int targetChunkChars) {
        if (current.isEmpty()) {
            current.append(segment);
            return;
        }

        if (current.length() + segment.length() + 2 <= targetChunkChars) {
            current.append("\n\n").append(segment);
            return;
        }

        chunks.add(current.toString().trim());
        current.setLength(0);
        current.append(segment);
    }

    private List<String> splitLongSegmentBySentence(String segment, int targetChunkChars) {
        List<String> sentenceChunks = new ArrayList<>();
        String[] sentences = segment.split("(?<=[。！？；.!?;])\\s*");
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (current.isEmpty()) {
                current.append(trimmed);
            } else if (current.length() + trimmed.length() + 1 <= targetChunkChars) {
                current.append(" ").append(trimmed);
            } else {
                sentenceChunks.add(current.toString().trim());
                current.setLength(0);
                current.append(trimmed);
            }
        }
        if (!current.isEmpty()) {
            sentenceChunks.add(current.toString().trim());
        }
        if (sentenceChunks.isEmpty()) {
            sentenceChunks.add(segment);
        }
        return sentenceChunks;
    }

    private List<String> mergeTinyChunks(List<String> chunks, int minChunkChars) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        List<String> merged = new ArrayList<>();
        for (String chunk : chunks) {
            if (!merged.isEmpty() && chunk.length() < minChunkChars) {
                String previous = merged.remove(merged.size() - 1);
                merged.add(previous + "\n\n" + chunk);
            } else {
                merged.add(chunk);
            }
        }
        return merged;
    }

    private List<String> applyOverlap(List<String> chunks, int overlapChars) {
        if (overlapChars <= 0 || chunks.size() <= 1) {
            return chunks;
        }
        List<String> result = new ArrayList<>();
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String current = chunks.get(i);
            int start = Math.max(0, previous.length() - overlapChars);
            String overlap = previous.substring(start).trim();
            if (overlap.isBlank()) {
                result.add(current);
            } else {
                result.add(overlap + "\n" + current);
            }
        }
        return result;
    }

    private Document withMetadata(Document source, Map<String, Object> extraMetadata) {
        return withMetadata(source, extraMetadata, source.getText());
    }

    private Document withMetadata(Document source, Map<String, Object> extraMetadata, String text) {
        Map<String, Object> metadata = source.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(source.getMetadata());
        metadata.putAll(extraMetadata);
        return new Document(source.getId(), text, metadata);
    }
}
