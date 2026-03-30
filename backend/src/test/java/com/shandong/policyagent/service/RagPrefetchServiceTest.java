package com.shandong.policyagent.service;

import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.rag.RagRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagPrefetchServiceTest {

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private KnowledgeReferenceService knowledgeReferenceService;

    private RagPrefetchService ragPrefetchService;

    @BeforeEach
    void setUp() throws Exception {
        ragPrefetchService = new RagPrefetchService(ragRetrievalService, knowledgeReferenceService);
        Field thresholdField = RagPrefetchService.class.getDeclaredField("directAnswerThreshold");
        thresholdField.setAccessible(true);
        thresholdField.set(ragPrefetchService, 0.86D);
    }

    @Test
    void shouldReturnEmptyResultWhenNoDocumentsRetrieved() {
        when(ragRetrievalService.retrieveRelevantDocuments(anyString())).thenReturn(List.of());

        RagPrefetchService.RagPrefetchResult result = ragPrefetchService.prefetch("山东家电以旧换新政策是什么");

        assertFalse(result.hasDocuments());
        assertFalse(result.canAnswerDirectly());
        assertEquals(0.0D, result.topScore());
    }

    @Test
    void shouldMarkDirectAnswerWhenTopScoreExceedsThreshold() {
        Document document = new Document(
                "doc-1",
                "这是政策原文片段。",
                Map.of(
                        "title", "2025年家电以旧换新实施细则",
                        "rerankScore", 0.91D
                )
        );
        ChatResponse.Reference reference = ChatResponse.Reference.builder()
                .title("2025年家电以旧换新实施细则")
                .url("https://example.com/policy")
                .build();

        when(ragRetrievalService.retrieveRelevantDocuments(anyString())).thenReturn(List.of(document));
        when(knowledgeReferenceService.buildReferences(any())).thenReturn(List.of(reference));

        RagPrefetchService.RagPrefetchResult result = ragPrefetchService.prefetch("山东以旧换新政策补贴条件");

        assertTrue(result.hasDocuments());
        assertTrue(result.canAnswerDirectly());
        assertEquals(0.91D, result.topScore());
        assertEquals(1, result.references().size());
        assertTrue(result.contextSnippet().contains("2025年家电以旧换新实施细则"));

        verify(ragRetrievalService).retrieveRelevantDocuments("山东以旧换新政策补贴条件");
        verify(knowledgeReferenceService).buildReferences(any());
    }
}