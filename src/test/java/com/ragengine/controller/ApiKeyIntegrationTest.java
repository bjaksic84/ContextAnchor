package com.ragengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragengine.BaseIntegrationTest;
import com.ragengine.TestAiConfig;
import com.ragengine.domain.dto.AuthResponse;
import com.ragengine.domain.dto.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for API Key management — create, list, revoke, and authenticate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiKeyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String apiKeyRaw;
    private String apiKeyId;

    @BeforeAll
    void registerUser() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "ApiKey User", "apikey@example.com",
                "Password123!", "ApiKey Org");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
    }

    @Test
    @Order(1)
    @DisplayName("Should create API key")
    void shouldCreateApiKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("name", "Test Key"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Key"))
                .andExpect(jsonPath("$.key").value(startsWith("ctx_")))
                .andExpect(jsonPath("$.prefix").isNotEmpty())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(), java.util.Map.class);
        apiKeyRaw = (String) response.get("key");
        apiKeyId = (String) response.get("id");
    }

    @Test
    @Order(2)
    @DisplayName("Should list API keys")
    void shouldListApiKeys() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].name").value("Test Key"))
                .andExpect(jsonPath("$[0].prefix").isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("Should authenticate using API key header")
    void shouldAuthenticateWithApiKey() throws Exception {
        // Access a protected endpoint using X-API-Key instead of JWT
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("X-API-Key", apiKeyRaw))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    @DisplayName("Should reject invalid API key")
    void shouldRejectInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("X-API-Key", "ctx_invalidkey12345678"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Should revoke API key")
    void shouldRevokeApiKey() throws Exception {
        mockMvc.perform(delete("/api/v1/api-keys/" + apiKeyId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(6)
    @DisplayName("Should reject revoked API key for authentication")
    void shouldRejectRevokedApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("X-API-Key", apiKeyRaw))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("Should require authentication to manage API keys")
    void shouldRequireAuthForApiKeys() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/api-keys")
                        .param("name", "Unauth Key"))
                .andExpect(status().isForbidden());
    }
}
