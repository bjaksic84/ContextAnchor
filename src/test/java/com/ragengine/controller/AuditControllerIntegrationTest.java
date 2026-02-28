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
 * Integration tests for AuditController — verifies audit log recording and querying.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String userId;

    @BeforeAll
    void registerUser() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "Audit User", "audit@example.com",
                "Password123!", "Audit Org");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
        userId = auth.user().id().toString();

        // Wait briefly for async audit logs to be persisted
        Thread.sleep(500);
    }

    @Test
    @DisplayName("Should return audit logs for authenticated user")
    void shouldReturnAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("Should filter audit logs by action")
    void shouldFilterByAction() throws Exception {
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("action", "USER_REGISTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Should return audit logs for specific user")
    void shouldReturnLogsForUser() throws Exception {
        mockMvc.perform(get("/api/v1/audit/user/" + userId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Should support pagination")
    void shouldSupportPagination() throws Exception {
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @DisplayName("Should require authentication for audit logs")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit"))
                .andExpect(status().isForbidden());
    }
}
