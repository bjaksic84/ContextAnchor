package com.ragengine;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests.
 * Provides a shared PostgreSQL + pgvector container via Testcontainers.
 * Disables Spring AI auto-configuration (no live OpenAI connection needed).
 *
 * Uses a single shared container across all test classes for efficiency
 * (JVM shutdown hook stops it automatically).
 */
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("ragdb_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("init-pgvector.sql");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Disable Spring AI auto-config that requires OpenAI/Ollama/pgvector
        registry.add("spring.autoconfigure.exclude", () -> String.join(",",
                "org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration",
                "org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration",
                "org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration"
        ));
    }
}
