package com.pgcluster.api.service;

import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.AuthResponse;
import com.pgcluster.api.model.dto.LoginRequest;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.UserRepository;
import com.pgcluster.api.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthService")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return AuthResponse with token on successful login")
        void shouldReturnAuthResponseOnSuccess() {
            User user = createTestUser();
            LoginRequest request = createLoginRequest("test@example.com", "password123");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
            when(jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole(), true))
                    .thenReturn("jwt-token");
            when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

            AuthResponse response = authService.login(request);

            assertThat(response.getToken()).isEqualTo("jwt-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(86400L);
            assertThat(response.getUser().getId()).isEqualTo(user.getId());
            assertThat(response.getUser().getEmail()).isEqualTo(user.getEmail());
            assertThat(response.getUser().getRole()).isEqualTo("user");

            verify(auditLogService).log(eq(AuditLog.AUTH_LOGIN_SUCCESS), eq(user), eq("auth"), isNull(), isNull());
        }

        @Test
        @DisplayName("should lowercase email before lookup")
        void shouldLowercaseEmail() {
            User user = createTestUser();
            LoginRequest request = createLoginRequest("User@Example.COM", "password123");

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
            when(jwtTokenProvider.generateToken(any(), any(), any(), anyBoolean())).thenReturn("token");
            when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

            authService.login(request);

            verify(userRepository).findByEmail("user@example.com");
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when user not found")
        void shouldThrowWhenUserNotFound() {
            LoginRequest request = createLoginRequest("unknown@example.com", "password");

            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(apiEx.getMessage()).isEqualTo("Invalid email or password");
                    });

            verify(auditLogService).logAuth(eq(AuditLog.AUTH_LOGIN_FAILURE), eq("unknown@example.com"), eq(false), eq("User not found"));
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when password is wrong")
        void shouldThrowWhenPasswordWrong() {
            User user = createTestUser();
            LoginRequest request = createLoginRequest("test@example.com", "wrongpassword");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpassword", user.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    });

            verify(auditLogService).logAuth(eq(AuditLog.AUTH_LOGIN_FAILURE), eq("test@example.com"), eq(false), eq("Invalid password"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN when account is disabled")
        void shouldThrowWhenAccountDisabled() {
            User user = createTestUser();
            user.setActive(false);
            LoginRequest request = createLoginRequest("test@example.com", "password123");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(apiEx.getMessage()).isEqualTo("Account is disabled");
                    });

            verify(auditLogService).logAuth(eq(AuditLog.AUTH_LOGIN_FAILURE), eq("test@example.com"), eq(false), eq("Account disabled"));
        }
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUser {

        @Test
        @DisplayName("should map all User fields to UserInfo")
        void shouldMapAllFields() {
            User user = createTestUser();

            AuthResponse.UserInfo info = authService.getCurrentUser(user);

            assertThat(info.getId()).isEqualTo(user.getId());
            assertThat(info.getEmail()).isEqualTo(user.getEmail());
            assertThat(info.getFirstName()).isEqualTo(user.getFirstName());
            assertThat(info.getLastName()).isEqualTo(user.getLastName());
            assertThat(info.getRole()).isEqualTo(user.getRole());
        }

        @Test
        @DisplayName("should handle null optional fields")
        void shouldHandleNullFields() {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("minimal@example.com")
                    .passwordHash("hash")
                    .role("user")
                    .active(true)
                    .build();

            AuthResponse.UserInfo info = authService.getCurrentUser(user);

            assertThat(info.getEmail()).isEqualTo("minimal@example.com");
            assertThat(info.getFirstName()).isNull();
            assertThat(info.getLastName()).isNull();
        }
    }

    private User createTestUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("encoded-password")
                .firstName("Test")
                .lastName("User")
                .role("user")
                .active(true)
                .build();
    }

    private LoginRequest createLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
