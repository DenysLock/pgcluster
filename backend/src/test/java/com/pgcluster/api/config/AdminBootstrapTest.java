package com.pgcluster.api.config;

import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AdminBootstrap")
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminBootstrap(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("should skip when admin user already exists")
    void shouldSkipWhenAdminExists() {
        User existingAdmin = User.builder().role("admin").build();
        when(userRepository.findAll()).thenReturn(List.of(existingAdmin));

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should skip when admin email is blank")
    void shouldSkipWhenEmailBlank() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "password123");

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should skip when admin password is blank")
    void shouldSkipWhenPasswordBlank() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "");

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should skip when password is less than 8 characters")
    void shouldSkipWhenPasswordTooShort() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "short");

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should promote existing user to admin")
    void shouldPromoteExistingUser() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "existing@test.com");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "password123");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("existing@test.com")
                .role("user")
                .build();
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);
        when(userRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(existingUser));

        bootstrap.run(null);

        assertThat(existingUser.getRole()).isEqualTo("admin");
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("should create new admin user when no existing user")
    void shouldCreateNewAdminUser() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "password123");

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");

        bootstrap.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@test.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(saved.getRole()).isEqualTo("admin");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("should lowercase email when creating admin")
    void shouldLowercaseEmail() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "Admin@Test.COM");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "password123");

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");

        bootstrap.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@test.com");
    }
}
