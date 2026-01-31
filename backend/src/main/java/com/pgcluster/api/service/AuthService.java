package com.pgcluster.api.service;

import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.AuthResponse;
import com.pgcluster.api.model.dto.LoginRequest;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.UserRepository;
import com.pgcluster.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase();

        // Find user by email
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            auditLogService.logAuth(AuditLog.AUTH_LOGIN_FAILURE, email, false, "User not found");
            throw new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditLogService.logAuth(AuditLog.AUTH_LOGIN_FAILURE, email, false, "Invalid password");
            throw new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        // Check if user is active
        if (!user.isActive()) {
            auditLogService.logAuth(AuditLog.AUTH_LOGIN_FAILURE, email, false, "Account disabled");
            throw new ApiException("Account is disabled", HttpStatus.FORBIDDEN);
        }

        // Log successful login
        auditLogService.log(AuditLog.AUTH_LOGIN_SUCCESS, user, "auth", null, null);

        log.info("User logged in: {}", user.getEmail());
        return createAuthResponse(user);
    }

    public AuthResponse.UserInfo getCurrentUser(User user) {
        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    private AuthResponse createAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isActive()
        );

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs() / 1000) // Convert to seconds
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole())
                        .build())
                .build();
    }
}
