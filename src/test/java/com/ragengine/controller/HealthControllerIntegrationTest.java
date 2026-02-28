package com.ragengine.controller;

import com.ragengine.BaseIntegrationTest;
import com.ragengine.TestAiConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for HealthController — health check endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiConfig.class)
class HealthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return UP health status")
    void shouldReturnUpStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ContextAnchor - Enterprise RAG Platform"));
    }

    @Test
    @DisplayName("Should include timestamp in health response")
    void shouldIncludeTimestamp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Should include uptime info")
    void shouldIncludeUptime() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uptime").isNotEmpty());
    }

    @Test
    @DisplayName("Should include database status")
    void shouldIncludeDatabaseStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.database").value("PostgreSQL"));
    }

    @Test
    @DisplayName("Should include AI provider info")
    void shouldIncludeAiProviderInfo() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.provider").isNotEmpty())
                .andExpect(jsonPath("$.ai.mode").isNotEmpty());
    }

    @Test
    @DisplayName("Should include Java runtime info")
    void shouldIncludeRuntimeInfo() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtime.javaVersion").isNotEmpty())
                .andExpect(jsonPath("$.runtime.javaVendor").isNotEmpty())
                .andExpect(jsonPath("$.runtime.maxMemoryMB").isNotEmpty())
                .andExpect(jsonPath("$.runtime.freeMemoryMB").isNotEmpty());
    }

    @Test
    @DisplayName("Health endpoint should be accessible without authentication")
    void shouldBeAccessibleWithoutAuth() throws Exception {
        // SecurityConfig permits /api/v1/health without auth
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }
}
