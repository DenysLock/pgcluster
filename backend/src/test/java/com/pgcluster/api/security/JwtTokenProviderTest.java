package com.pgcluster.api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String VALID_SECRET = "test-jwt-secret-that-is-at-least-32-characters-long-for-testing";
    private static final long EXPIRATION_MS = 86400000L; // 24 hours

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", VALID_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", EXPIRATION_MS);
        jwtTokenProvider.init();
    }

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("should throw exception when JWT secret is null")
        void shouldThrowWhenSecretIsNull() {
            JwtTokenProvider provider = new JwtTokenProvider();
            ReflectionTestUtils.setField(provider, "jwtSecret", null);
            ReflectionTestUtils.setField(provider, "jwtExpiration", EXPIRATION_MS);

            assertThatThrownBy(provider::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET environment variable must be set");
        }

        @Test
        @DisplayName("should throw exception when JWT secret is blank")
        void shouldThrowWhenSecretIsBlank() {
            JwtTokenProvider provider = new JwtTokenProvider();
            ReflectionTestUtils.setField(provider, "jwtSecret", "   ");
            ReflectionTestUtils.setField(provider, "jwtExpiration", EXPIRATION_MS);

            assertThatThrownBy(provider::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET environment variable must be set");
        }

        @Test
        @DisplayName("should throw exception when JWT secret is too short")
        void shouldThrowWhenSecretIsTooShort() {
            JwtTokenProvider provider = new JwtTokenProvider();
            ReflectionTestUtils.setField(provider, "jwtSecret", "short-secret");
            ReflectionTestUtils.setField(provider, "jwtExpiration", EXPIRATION_MS);

            assertThatThrownBy(provider::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET must be at least 32 characters");
        }

        @Test
        @DisplayName("should initialize successfully with valid secret")
        void shouldInitializeWithValidSecret() {
            JwtTokenProvider provider = new JwtTokenProvider();
            ReflectionTestUtils.setField(provider, "jwtSecret", VALID_SECRET);
            ReflectionTestUtils.setField(provider, "jwtExpiration", EXPIRATION_MS);

            // Should not throw
            provider.init();
        }
    }

    @Nested
    @DisplayName("Token Generation")
    class TokenGeneration {

        @Test
        @DisplayName("should generate valid token with all claims")
        void shouldGenerateValidToken() {
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            String role = "USER";
            boolean active = true;

            String token = jwtTokenProvider.generateToken(userId, email, role, active);

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
            assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo(email);
            assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo(role);
            assertThat(jwtTokenProvider.getActiveFromToken(token)).isEqualTo(active);
        }

        @Test
        @DisplayName("should include active=false in token")
        void shouldIncludeInactiveStatus() {
            UUID userId = UUID.randomUUID();
            String token = jwtTokenProvider.generateToken(userId, "test@example.com", "USER", false);

            assertThat(jwtTokenProvider.getActiveFromToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {

        @Test
        @DisplayName("should validate correct token")
        void shouldValidateCorrectToken() {
            String token = jwtTokenProvider.generateToken(
                    UUID.randomUUID(), "test@example.com", "USER", true);

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("should reject malformed token")
        void shouldRejectMalformedToken() {
            assertThat(jwtTokenProvider.validateToken("not.a.valid.token")).isFalse();
        }

        @Test
        @DisplayName("should reject token signed with different key")
        void shouldRejectTokenWithWrongSignature() {
            // Create a token with a different secret
            String differentSecret = "another-secret-that-is-at-least-32-characters-long";
            SecretKey differentKey = Keys.hmacShaKeyFor(
                    differentSecret.getBytes(StandardCharsets.UTF_8));

            String tokenWithWrongKey = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("email", "test@example.com")
                    .claim("role", "USER")
                    .claim("active", true)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                    .signWith(differentKey)
                    .compact();

            assertThat(jwtTokenProvider.validateToken(tokenWithWrongKey)).isFalse();
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            // Create an expired token
            SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("email", "test@example.com")
                    .claim("role", "USER")
                    .claim("active", true)
                    .issuedAt(new Date(System.currentTimeMillis() - 200000))
                    .expiration(new Date(System.currentTimeMillis() - 100000))
                    .signWith(key)
                    .compact();

            assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("should reject empty token")
        void shouldRejectEmptyToken() {
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("should reject null token")
        void shouldRejectNullToken() {
            assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Claim Extraction")
    class ClaimExtraction {

        @Test
        @DisplayName("should extract user ID from token")
        void shouldExtractUserId() {
            UUID userId = UUID.randomUUID();
            String token = jwtTokenProvider.generateToken(userId, "test@example.com", "USER", true);

            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
        }

        @Test
        @DisplayName("should extract email from token")
        void shouldExtractEmail() {
            String email = "unique@example.com";
            String token = jwtTokenProvider.generateToken(UUID.randomUUID(), email, "USER", true);

            assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo(email);
        }

        @Test
        @DisplayName("should extract role from token")
        void shouldExtractRole() {
            String role = "ADMIN";
            String token = jwtTokenProvider.generateToken(UUID.randomUUID(), "test@example.com", role, true);

            assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo(role);
        }

        @Test
        @DisplayName("should extract all claims")
        void shouldExtractAllClaims() {
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            String role = "USER";
            boolean active = true;

            String token = jwtTokenProvider.generateToken(userId, email, role, active);
            var claims = jwtTokenProvider.getAllClaims(token);

            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.get("email", String.class)).isEqualTo(email);
            assertThat(claims.get("role", String.class)).isEqualTo(role);
            assertThat(claims.get("active", Boolean.class)).isEqualTo(active);
        }
    }
}
