package com.ragengine;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;

/**
 * Test configuration that provides stub implementations for Spring AI beans.
 * These replace the auto-configured beans that require a live OpenAI connection.
 */
@TestConfiguration
public class TestAiConfig {

    @Bean
    public VectorStore vectorStore() {
        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {
                // No-op for tests
            }

            @Override
            public void delete(List<String> idList) {
                // No-op for tests
            }

            @Override
            public void delete(Filter.Expression expression) {
                // No-op for tests
            }

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                return Collections.emptyList();
            }
        };
    }

    @Bean
    public ChatClient.Builder chatClientBuilder() {
        ChatModel stubModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of());
            }

            @Override
            public String call(String message) {
                return "Test response";
            }
        };
        return ChatClient.builder(stubModel);
    }
}
