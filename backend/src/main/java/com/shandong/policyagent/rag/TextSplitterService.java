package com.shandong.policyagent.rag;

import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
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
    private static final int MIN_CHUNK_SIZE = 100;

    private final RagConfig ragConfig;
    private final KnowledgeConfigRepository knowledgeConfigRepository;
    private final EmbeddingService embeddingService;

    public List<Document> splitDocuments(List<Document> documents) {
        return splitDocuments(documents, null);
    }

    public List<Document> splitDocuments(List<Document> documents, String embeddingModelId) {
        ChunkingSettings chunkConfig = resolveChunkingSettings(embeddingModelId);

        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkConfig.defaultChunkSize(),
                chunkConfig.minChunkSizeChars(),
                chunkConfig.minChunkLengthToEmbed(),
                chunkConfig.chunkOverlap(),
                chunkConfig.keepSeparator()
        );

        List<Document> splitDocs = new ArrayList<>();
        int passthroughCount = 0;
        int semanticSplitCount = 0;

        for (Document document : documents) {
            String text = document.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (text.length() <= chunkConfig.noSplitMaxChars()) {
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
        return splitDocuments(List.of(document), null);
    }

    private List<Document> splitBySemantics(Document document,
                                            ChunkingSettings chunkConfig,
                                            TokenTextSplitter fallbackSplitter) {
        String normalizedText = normalizeText(document.getText());
        int targetChunkChars = Math.max(chunkConfig.defaultChunkSize(), chunkConfig.minChunkSizeChars());
        int overlapChars = Math.max(0, chunkConfig.chunkOverlap());

        List<String> segments = splitToSegments(normalizedText);
        List<String> baseChunks = assembleChunks(
                segments,
                targetChunkChars,
                chunkConfig.minChunkSizeChars(),
                chunkConfig.noSplitMaxChars()
        );

        if (baseChunks.isEmpty()) {
            return fallbackSplitter.apply(List.of(document));
        }

        List<String> overlappedChunks = applyOverlap(baseChunks, overlapChars, chunkConfig.noSplitMaxChars());
        List<Document> result = new ArrayList<>();
        for (String chunk : overlappedChunks) {
            String cleanChunk = chunk == null ? "" : chunk.trim();
            if (cleanChunk.isBlank()) {
                continue;
            }
            for (String normalizedChunk : enforceHardLimit(cleanChunk, chunkConfig.noSplitMaxChars())) {
                result.add(withMetadata(document, Map.of(
                        "splitStrategy", SPLIT_STRATEGY,
                        "chunkChars", normalizedChunk.length()
                ), normalizedChunk));
            }
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

    private List<String> assembleChunks(List<String> segments,
                                        int targetChunkChars,
                                        int minChunkChars,
                                        int hardMaxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String segment : segments) {
            if (segment.length() > targetChunkChars * 1.4) {
                List<String> sentenceChunks = splitLongSegmentBySentence(segment, targetChunkChars, hardMaxChars);
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

    private List<String> splitLongSegmentBySentence(String segment, int targetChunkChars, int hardMaxChars) {
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
        List<String> normalizedChunks = new ArrayList<>();
        for (String sentenceChunk : sentenceChunks) {
            normalizedChunks.addAll(enforceHardLimit(sentenceChunk, hardMaxChars));
        }
        return normalizedChunks;
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

    private List<String> applyOverlap(List<String> chunks, int overlapChars, int hardMaxChars) {
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
                String merged = overlap + "\n" + current;
                if (merged.length() <= hardMaxChars) {
                    result.add(merged);
                } else {
                    result.add(current);
                }
            }
        }
        return result;
    }

    private List<String> enforceHardLimit(String text, int hardMaxChars) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        if (normalized.length() <= hardMaxChars) {
            return List.of(normalized);
        }

        List<String> parts = new ArrayList<>();
        String remaining = normalized;
        while (remaining.length() > hardMaxChars) {
            int splitIndex = findSplitIndex(remaining, hardMaxChars);
            String part = remaining.substring(0, splitIndex).trim();
            if (!part.isBlank()) {
                parts.add(part);
            }
            remaining = remaining.substring(splitIndex).trim();
        }
        if (!remaining.isBlank()) {
            parts.add(remaining);
        }
        return parts;
    }

    private int findSplitIndex(String text, int hardMaxChars) {
        int minBoundary = Math.max(MIN_CHUNK_SIZE, hardMaxChars / 2);
        for (int index = hardMaxChars; index >= minBoundary; index--) {
            char current = text.charAt(index - 1);
            if (Character.isWhitespace(current)
                    || current == '\n'
                    || current == '。'
                    || current == '！'
                    || current == '？'
                    || current == '；'
                    || current == ','
                    || current == '，'
                    || current == '.'
                    || current == ';') {
                return index;
            }
        }
        return hardMaxChars;
    }

    private ChunkingSettings resolveChunkingSettings(String preferredEmbeddingModelId) {
        RagConfig.Chunking defaults = ragConfig.getChunking();
        KnowledgeConfig persistedConfig = knowledgeConfigRepository.findById(1L).orElse(null);
        String resolvedEmbeddingModelId = resolveEmbeddingModelId(preferredEmbeddingModelId, persistedConfig);
        int embeddingMaxChars = embeddingService.resolveMaxInputChars(resolvedEmbeddingModelId);

        int baseChunkSize = persistedConfig != null && persistedConfig.getChunkSize() != null
                ? persistedConfig.getChunkSize()
                : defaults.getDefaultChunkSize();
        int baseMinChunkSize = persistedConfig != null && persistedConfig.getMinChunkSizeChars() != null
                ? persistedConfig.getMinChunkSizeChars()
                : defaults.getMinChunkSizeChars();
        int baseOverlap = persistedConfig != null && persistedConfig.getChunkOverlap() != null
                ? persistedConfig.getChunkOverlap()
                : defaults.getChunkOverlap();
        int baseNoSplitMaxChars = persistedConfig != null && persistedConfig.getNoSplitMaxChars() != null
                ? persistedConfig.getNoSplitMaxChars()
                : defaults.getNoSplitMaxChars();

        int safeChunkSize = clamp(baseChunkSize, MIN_CHUNK_SIZE, embeddingMaxChars);
        int safeMinChunkSize = clamp(baseMinChunkSize, Math.min(50, safeChunkSize), safeChunkSize);
        int safeOverlap = clamp(baseOverlap, 0, Math.max(0, safeChunkSize / 3));
        int safeNoSplitMaxChars = clamp(baseNoSplitMaxChars, safeChunkSize, embeddingMaxChars);

        if (baseChunkSize != safeChunkSize || baseNoSplitMaxChars != safeNoSplitMaxChars) {
            log.info("知识库切片参数已按嵌入模型安全阈值收敛 | model={} | chunkSize={} -> {} | noSplitMaxChars={} -> {}",
                    resolvedEmbeddingModelId,
                    baseChunkSize,
                    safeChunkSize,
                    baseNoSplitMaxChars,
                    safeNoSplitMaxChars);
        }

        return new ChunkingSettings(
                safeChunkSize,
                safeMinChunkSize,
                defaults.getMinChunkLengthToEmbed(),
                safeOverlap,
                defaults.isKeepSeparator(),
                safeNoSplitMaxChars
        );
    }

    private String resolveEmbeddingModelId(String preferredEmbeddingModelId, KnowledgeConfig persistedConfig) {
        if (preferredEmbeddingModelId != null && !preferredEmbeddingModelId.isBlank()) {
            return embeddingService.resolveDefaultModelId(preferredEmbeddingModelId);
        }
        if (persistedConfig != null && persistedConfig.getDefaultEmbeddingModel() != null) {
            return embeddingService.resolveDefaultModelId(persistedConfig.getDefaultEmbeddingModel());
        }
        return embeddingService.resolveDefaultModelId(null);
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
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

    private record ChunkingSettings(
            int defaultChunkSize,
            int minChunkSizeChars,
            int minChunkLengthToEmbed,
            int chunkOverlap,
            boolean keepSeparator,
            int noSplitMaxChars
    ) {
    }
}
