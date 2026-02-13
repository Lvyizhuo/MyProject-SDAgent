package com.shandong.policyagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RerankingVectorStoreTest {

    @Test
    void shouldNormalizeDocumentIdBeforeDelegateAdd() {
        CapturingVectorStore delegate = new CapturingVectorStore();
        RagConfig ragConfig = new RagConfig();
        RerankingVectorStore store = new RerankingVectorStore(delegate, null, ragConfig);

        Document doc = new Document(
                "not-a-uuid-" + "x".repeat(100),
                "cached web search text",
                Map.of("source", "web-search")
        );

        store.add(List.of(doc));

        assertEquals(1, delegate.added.size());
        Document normalized = delegate.added.getFirst();
        assertEquals("cached web search text", normalized.getText());
        assertDoesNotThrow(() -> UUID.fromString(normalized.getId()));
    }

    private static class CapturingVectorStore implements VectorStore {
        private final List<Document> added = new ArrayList<>();

        @Override
        public String getName() {
            return "capturing";
        }

        @Override
        public void add(List<Document> documents) {
            this.added.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }
    }
}
