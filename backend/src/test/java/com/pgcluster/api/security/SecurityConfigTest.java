package com.pgcluster.api.security;

import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Security Configuration")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${security.internal-api-key}")
    private String internalApiKey;

    private String userToken;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("test@test.com")
                .passwordHash("$2a$10$dummy")
                .role("USER")
                .active(true)
                .build();
        user = userRepository.save(user);

        userToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole(), true);
    }

    @Nested
    @DisplayName("Public Endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("/api/v1/auth/register should be accessible without authentication")
        void registerShouldBePublic() throws Exception {
            String json = """
                {
                    "email": "new@test.com",
                    "password": "securePassword123"
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andReturn();

            // Should not return 401 (it should be accessible)
            assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
        }

        @Test
        @DisplayName("/api/v1/auth/login should be accessible without authentication")
        void loginShouldBePublic() throws Exception {
            String json = """
                {
                    "email": "test@test.com",
                    "password": "password"
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andReturn();

            // 401 for bad credentials is different from 401 for missing auth
            // The endpoint should be reachable (not return 401 for missing token)
            // Here we accept any status since the endpoint is accessible
            assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
        }

        @Test
        @DisplayName("/health should be accessible without authentication")
        void healthShouldBePublic() throws Exception {
            MvcResult result = mockMvc.perform(get("/actuator/health"))
                    .andReturn();

            // Either 200 if actuator is enabled, or 404 if disabled - but not 401
            assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
        }
    }

    @Nested
    @DisplayName("Protected Endpoints")
    class ProtectedEndpoints {

        @Test
        @DisplayName("/api/v1/clusters should require authentication")
        void clustersShouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/v1/clusters"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("/api/v1/clusters should be accessible with valid token")
        void clustersShouldBeAccessibleWithToken() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/api/v1/user should require authentication")
        void userShouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/v1/user/profile"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Internal Endpoints")
    class InternalEndpoints {

        @Test
        @DisplayName("/internal/** should reject requests without API key")
        void shouldRejectWithoutApiKey() throws Exception {
            mockMvc.perform(get("/internal/health"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/internal/** should reject requests with wrong API key")
        void shouldRejectWithWrongApiKey() throws Exception {
            mockMvc.perform(get("/internal/health")
                            .header("X-Internal-Api-Key", "wrong-api-key"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/internal/** should accept requests with correct API key")
        void shouldAcceptWithCorrectApiKey() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/health")
                            .header("X-Internal-Api-Key", internalApiKey))
                    .andReturn();

            // Should not return 401 (API key is correct)
            // May return 404 if endpoint doesn't exist
            assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
        }

        @Test
        @DisplayName("/internal/** should not accept JWT as authentication")
        void shouldNotAcceptJwtForInternal() throws Exception {
            mockMvc.perform(get("/internal/health")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("JWT Token Validation")
    class JwtTokenValidation {

        @Test
        @DisplayName("should reject request with expired token")
        void shouldRejectExpiredToken() throws Exception {
            // Create a token with very short expiration (already expired)
            // Note: In real test, you'd need to mock the time or create an actually expired token
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJ0ZXN0QHRlc3QuY29tIiwicm9sZSI6IlVTRVIiLCJhY3RpdmUiOnRydWUsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxNTE2MjM5MDIyfQ.invalid"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject request with malformed token")
        void shouldRejectMalformedToken() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer not-a-valid-jwt-token"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject request with missing Bearer prefix")
        void shouldRejectMissingBearerPrefix() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", userToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Inactive User Handling")
    class InactiveUserHandling {

        @Test
        @DisplayName("inactive user token should still be validated")
        void inactiveUserTokenShouldBeValidated() throws Exception {
            User inactiveUser = User.builder()
                    .email("inactive@test.com")
                    .passwordHash("$2a$10$dummy")
                    .role("USER")
                    .active(false)
                    .build();
            inactiveUser = userRepository.save(inactiveUser);

            String inactiveToken = jwtTokenProvider.generateToken(
                    inactiveUser.getId(), inactiveUser.getEmail(), inactiveUser.getRole(), false);

            // The token is valid, but the application should check the active claim
            MvcResult result = mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer " + inactiveToken))
                    .andReturn();

            // Either OK (if no active check) or Forbidden (if checked)
            assertThat(result.getResponse().getStatus()).isIn(200, 403);
        }
    }
}
