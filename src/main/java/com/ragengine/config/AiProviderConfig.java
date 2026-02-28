package com.ragengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.micrometer.observation.ObservationRegistry;

/**
 * AI provider configuration — supports switching between OpenAI (cloud) and Ollama (local).
 *
 * <p>Controlled by the {@code rag.ai.provider} property:</p>
 * <ul>
 *   <li>{@code openai} (default) — uses OpenAI API for chat and embeddings</li>
 *   <li>{@code ollama} — uses locally running Ollama for fully private, zero-external-API inference</li>
 * </ul>
 *
 * <p>Spring profiles provide convenient switching:</p>
 * <ul>
 *   <li>Default profile → OpenAI</li>
 *   <li>{@code local} profile → Ollama (see application-local.yml)</li>
 * </ul>
 */
@Configuration
@Slf4j
public class AiProviderConfig {

    // ================================================================
    // OpenAI provider (default)
    // ================================================================

    @Configuration
    @ConditionalOnProperty(name = "rag.ai.provider", havingValue = "openai", matchIfMissing = true)
    static class OpenAiProviderConfig {

        @Bean
        @Primary
        @ConditionalOnMissingBean(OpenAiApi.class)
        public OpenAiApi openAiApi(
                @Value("${spring.ai.openai.api-key}") String apiKey) {
            log.info("Configuring OpenAI provider");
            return new OpenAiApi(apiKey);
        }

        @Bean
        @Primary
        @ConditionalOnMissingBean(ChatModel.class)
        public ChatModel chatModel(OpenAiApi openAiApi,
                                   @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
                                   @Value("${spring.ai.openai.chat.options.temperature:0.3}") double temperature) {
            log.info("Initializing OpenAI ChatModel: model={}, temperature={}", model, temperature);
            return new OpenAiChatModel(openAiApi,
                    OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(temperature)
                            .build());
        }

        @Bean
        @Primary
        @ConditionalOnMissingBean(EmbeddingModel.class)
        public EmbeddingModel embeddingModel(OpenAiApi openAiApi,
                                              @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String model) {
            log.info("Initializing OpenAI EmbeddingModel: model={}", model);
            return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model(model)
                            .build());
        }

        @Bean
        @Primary
        @ConditionalOnMissingBean(ChatClient.Builder.class)
        public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
            return ChatClient.builder(chatModel);
        }
    }

    // ================================================================
    // Ollama provider (local / private mode)
    // ================================================================

    @Configuration
    @ConditionalOnProperty(name = "rag.ai.provider", havingValue = "ollama")
    static class OllamaProviderConfig {

        @Bean
        @Primary
        @ConditionalOnMissingBean(OllamaApi.class)
        public OllamaApi ollamaApi(
                @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
            log.info("Configuring Ollama provider at: {}", baseUrl);
            return new OllamaApi(baseUrl);
        }

        @Bean
        @Primary
        @ConditionalOnMissingBean(ChatModel.class)
        public ChatModel chatModel(OllamaApi ollamaApi,
                                   @Value("${spring.ai.ollama.chat.options.model:llama3.2:3b}") String model,
                                   @Value("${spring.ai.ollama.chat.options.temperature:0.3}") double temperature,
                                   ObservationRegistry observationRegistry) {
            log.info("Initializing Ollama ChatModel: model={}, temperature={}", model, temperature);
            OllamaOptions chatOptions = OllamaOptions.builder()
                    .model(model)
                    .temperature(temperature)
                    .build();
            return OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(chatOptions)
                    .observationRegistry(observationRegistry)
                    .modelManagementOptions(ModelManagementOptions.defaults())
                    .build();
        }

        @Bean
        @Primary
        @ConditionalOnMissingBean(EmbeddingModel.class)
        public EmbeddingModel embeddingModel(OllamaApi ollamaApi,
                                              @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String model,
                                              ObservationRegistry observationRegistry) {
            log.info("Initializing Ollama EmbeddingModel: model={}", model);
            OllamaOptions embeddingOptions = OllamaOptions.builder()
                    .model(model)
                    .build();
            return new OllamaEmbeddingModel(ollamaApi, embeddingOptions,
                    observationRegistry, ModelManagementOptions.defaults());
        }

        @Bean
        @Primary
        @ConditionalOnMissingBean(ChatClient.Builder.class)
        public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
            return ChatClient.builder(chatModel);
        }
    }
}
