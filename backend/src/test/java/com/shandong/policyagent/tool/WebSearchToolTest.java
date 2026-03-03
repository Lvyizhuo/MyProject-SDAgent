package com.shandong.policyagent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    @Test
    void shouldReturnValidationMessageWhenQueryIsBlank() {
        VectorStore noopVectorStore = new VectorStore() {
            @Override
            public String getName() {
                return "noop";
            }

            @Override
            public void add(List<Document> documents) {
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
        };

        // Create mock ToolFailurePolicyCenter with default constructor
        ToolFailurePolicyCenter failurePolicyCenter = new ToolFailurePolicyCenter();

        // Create mock ToolStateManager that always returns true for enabled checks
        ToolStateManager toolStateManager = new ToolStateManager(null) {
            @Override
            public boolean isEnabled(String skillKey) {
                return true;
            }
        };

        WebSearchTool tool = new WebSearchTool(noopVectorStore, failurePolicyCenter, toolStateManager);

        WebSearchTool.SearchResponse response = tool.webSearch()
                .apply(new WebSearchTool.SearchRequest("   ", 5));

        assertEquals(0, response.totalResults());
        assertTrue(response.summary().contains("搜索关键词为空"));
        assertEquals(List.of(), response.results());
    }
}
