package com.shandong.policyagent.rag;

import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextSplitterServiceTest {

    @Mock
    private KnowledgeConfigRepository knowledgeConfigRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Test
    void shouldClampChunksToEmbeddingSafeLength() {
        RagConfig ragConfig = new RagConfig();
        KnowledgeConfig knowledgeConfig = KnowledgeConfig.builder()
                .chunkSize(1600)
                .chunkOverlap(300)
                .minChunkSizeChars(350)
                .noSplitMaxChars(6000)
                .defaultEmbeddingModel("ollama:nomic-embed-text")
                .build();
        TextSplitterService service = new TextSplitterService(ragConfig, knowledgeConfigRepository, embeddingService);
        Document source = new Document("doc-1", "甲".repeat(2500), Map.of());

        when(knowledgeConfigRepository.findById(1L)).thenReturn(Optional.of(knowledgeConfig));
        when(embeddingService.resolveDefaultModelId("ollama:nomic-embed-text")).thenReturn("ollama:nomic-embed-text");
        when(embeddingService.resolveMaxInputChars("ollama:nomic-embed-text")).thenReturn(900);

        List<Document> chunks = service.splitDocuments(List.of(source));

        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getText().length() <= 900));
    }
}
