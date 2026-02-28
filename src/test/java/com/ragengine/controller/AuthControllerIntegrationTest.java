package com.ragengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragengine.BaseIntegrationTest;
import com.ragengine.TestAiConfig;
import com.ragengine.domain.dto.AuthResponse;
import com.ragengine.domain.dto.LoginRequest;
import com.ragengine.domain.dto.RefreshTokenRequest;
import com.ragengine.domain.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController — register, login, refresh, logout flows.
 * Uses Testcontainers PostgreSQL with pgvector.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String AUTH_BASE = "/api/v1/auth";

    @Test
    @DisplayName("Should register a new user successfully")
    void shouldRegisterUser() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Integration Test User", "inttest@example.com",
                "Password123!", "IntTest Org");

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("inttest@example.com"))
                .andExpect(jsonPath("$.user.fullName").value("Integration Test User"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.tenantName").value("IntTest Org"));
    }

    @Test
    @DisplayName("Should reject duplicate email registration")
    void shouldRejectDuplicateRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Dup User", "dup@example.com",
                "Password123!", "Dup Org");

        // First registration
        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second with same email
        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject registration with invalid email")
    void shouldRejectInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Test User", "not-an-email",
                "Password123!", "Test Org");

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should login with correct credentials")
    void shouldLoginSuccessfully() throws Exception {
        // Register first
        RegisterRequest reg = new RegisterRequest(
                "Login User", "login@example.com",
                "Password123!", "Login Org");
        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Login
        LoginRequest login = new LoginRequest("login@example.com", "Password123!");
        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login@example.com"));
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void shouldRejectWrongPassword() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "Bad Login User", "badlogin@example.com",
                "Password123!", "BadLogin Org");
        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("badlogin@example.com", "WrongPassword!");
        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should refresh access token")
    void shouldRefreshToken() throws Exception {
        // Register and capture refresh token
        RegisterRequest reg = new RegisterRequest(
                "Refresh User", "refresh@example.com",
                "Password123!", "Refresh Org");
        MvcResult result = mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);

        // Refresh
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(authResponse.refreshToken());
        mockMvc.perform(post(AUTH_BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("Should reject refresh with invalid token")
    void shouldRejectInvalidRefreshToken() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest("invalid-refresh-token");
        mockMvc.perform(post(AUTH_BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should logout and invalidate refresh token")
    void shouldLogout() throws Exception {
        // Register
        RegisterRequest reg = new RegisterRequest(
                "Logout User", "logout@example.com",
                "Password123!", "Logout Org");
        MvcResult result = mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);

        // Logout
        mockMvc.perform(post(AUTH_BASE + "/logout")
                        .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully logged out"));

        // Refresh token should now be invalid
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(authResponse.refreshToken());
        mockMvc.perform(post(AUTH_BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject access to protected endpoint without token")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isForbidden());
    }
}
