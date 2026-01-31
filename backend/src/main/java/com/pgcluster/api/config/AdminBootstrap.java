package com.pgcluster.api.config;

import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Check if any admin exists
        boolean adminExists = userRepository.findAll().stream()
                .anyMatch(u -> "admin".equalsIgnoreCase(u.getRole()));

        if (adminExists) {
            log.info("Admin user already exists, skipping bootstrap");
            return;
        }

        // Validate environment variables
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("ADMIN_EMAIL not configured, skipping admin bootstrap. Set ADMIN_EMAIL and ADMIN_PASSWORD to create initial admin.");
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD not configured, skipping admin bootstrap");
            return;
        }

        if (adminPassword.length() < 8) {
            log.warn("ADMIN_PASSWORD must be at least 8 characters, skipping admin bootstrap");
            return;
        }

        // Check if user with this email already exists
        if (userRepository.existsByEmail(adminEmail.toLowerCase())) {
            // Promote existing user to admin
            User existingUser = userRepository.findByEmail(adminEmail.toLowerCase()).orElseThrow();
            existingUser.setRole("admin");
            userRepository.save(existingUser);
            log.info("Promoted existing user to admin: {}", adminEmail);
            return;
        }

        // Create new admin user
        User admin = User.builder()
                .email(adminEmail.toLowerCase())
                .passwordHash(passwordEncoder.encode(adminPassword))
                .firstName("Admin")
                .lastName("User")
                .role("admin")
                .active(true)
                .build();

        userRepository.save(admin);
        log.info("Created bootstrap admin user: {}", adminEmail);
    }
}
