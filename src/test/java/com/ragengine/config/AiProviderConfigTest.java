package com.ragengine.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AiProviderConfig — verifies that the correct provider beans
 * are created based on the rag.ai.provider property.
 */
class AiProviderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiProviderConfig.class);

    @Test
    @DisplayName("OpenAI beans are created when provider=openai")
    void openAiBeansCreated_whenProviderIsOpenAi() {
        contextRunner
                .withPropertyValues(
                        "rag.ai.provider=openai",
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.chat.options.model=gpt-4o-mini",
                        "spring.ai.openai.chat.options.temperature=0.3",
                        "spring.ai.openai.embedding.options.model=text-embedding-3-small"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAiApi.class);
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context).hasSingleBean(ChatClient.Builder.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
                    assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OpenAiEmbeddingModel.class);
                });
    }

    @Test
    @DisplayName("OpenAI beans are created by default (matchIfMissing=true)")
    void openAiBeansCreated_whenNoProviderSpecified() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.chat.options.model=gpt-4o-mini",
                        "spring.ai.openai.chat.options.temperature=0.3",
                        "spring.ai.openai.embedding.options.model=text-embedding-3-small"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
                });
    }

    @Test
    @DisplayName("Ollama beans are NOT created when provider=openai")
    void ollamaBeansNotCreated_whenProviderIsOpenAi() {
        contextRunner
                .withPropertyValues(
                        "rag.ai.provider=openai",
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.chat.options.model=gpt-4o-mini",
                        "spring.ai.openai.chat.options.temperature=0.3",
                        "spring.ai.openai.embedding.options.model=text-embedding-3-small"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(org.springframework.ai.ollama.api.OllamaApi.class);
                });
    }

    @Test
    @DisplayName("OpenAI beans are NOT created when provider=ollama")
    void openAiBeansNotCreated_whenProviderIsOllama() {
        contextRunner
                .withPropertyValues(
                        "rag.ai.provider=ollama",
                        "spring.ai.ollama.base-url=http://localhost:11434",
                        "spring.ai.ollama.chat.options.model=llama3.1",
                        "spring.ai.ollama.chat.options.temperature=0.3",
                        "spring.ai.ollama.embedding.options.model=nomic-embed-text"
                )
                .withBean(io.micrometer.observation.ObservationRegistry.class,
                        io.micrometer.observation.ObservationRegistry::create)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenAiApi.class);
                    assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
                });
    }

    @Test
    @DisplayName("Ollama beans are created when provider=ollama")
    void ollamaBeansCreated_whenProviderIsOllama() {
        contextRunner
                .withPropertyValues(
                        "rag.ai.provider=ollama",
                        "spring.ai.ollama.base-url=http://localhost:11434",
                        "spring.ai.ollama.chat.options.model=llama3.1",
                        "spring.ai.ollama.chat.options.temperature=0.3",
                        "spring.ai.ollama.embedding.options.model=nomic-embed-text"
                )
                .withBean(io.micrometer.observation.ObservationRegistry.class,
                        io.micrometer.observation.ObservationRegistry::create)
                .run(context -> {
                    assertThat(context).hasSingleBean(org.springframework.ai.ollama.api.OllamaApi.class);
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context).hasSingleBean(ChatClient.Builder.class);
                    assertThat(context.getBean(ChatModel.class))
                            .isInstanceOf(org.springframework.ai.ollama.OllamaChatModel.class);
                    assertThat(context.getBean(EmbeddingModel.class))
                            .isInstanceOf(org.springframework.ai.ollama.OllamaEmbeddingModel.class);
                });
    }
}
