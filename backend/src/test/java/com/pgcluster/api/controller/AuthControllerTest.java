package com.pgcluster.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.UserRepository;
import com.pgcluster.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Auth Controller")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private String validToken;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("auth@test.com")
                .passwordHash(passwordEncoder.encode("correctPassword"))
                .role("USER")
                .active(true)
                .firstName("Auth")
                .lastName("User")
                .build();
        testUser = userRepository.save(testUser);
        validToken = jwtTokenProvider.generateToken(testUser.getId(), testUser.getEmail(), testUser.getRole(), true);
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("should return token for valid credentials")
        void shouldReturnTokenForValidCredentials() throws Exception {
            String json = """
                {"email": "auth@test.com", "password": "correctPassword"}
                """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", notNullValue()))
                    .andExpect(jsonPath("$.tokenType", is("Bearer")))
                    .andExpect(jsonPath("$.user.email", is("auth@test.com")));
        }

        @Test
        @DisplayName("should return 401 for wrong password")
        void shouldRejectWrongPassword() throws Exception {
            String json = """
                {"email": "auth@test.com", "password": "wrongPassword"}
                """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 401 for non-existent email")
        void shouldRejectNonExistentEmail() throws Exception {
            String json = """
                {"email": "nobody@test.com", "password": "password"}
                """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 for missing fields")
        void shouldRejectMissingFields() throws Exception {
            String json = """
                {"email": ""}
                """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 403 for disabled user")
        void shouldRejectDisabledUser() throws Exception {
            testUser.setActive(false);
            userRepository.save(testUser);

            String json = """
                {"email": "auth@test.com", "password": "correctPassword"}
                """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me")
    class GetCurrentUser {

        @Test
        @DisplayName("should return current user info with valid JWT")
        void shouldReturnCurrentUser() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is("auth@test.com")))
                    .andExpect(jsonPath("$.firstName", is("Auth")));
        }

        @Test
        @DisplayName("should return 401 without authentication")
        void shouldReturn401WithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("should return success message")
        void shouldReturnSuccessMessage() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", is("Logged out successfully")));
        }
    }
}
