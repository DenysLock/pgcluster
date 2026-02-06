package com.pgcluster.api.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Entity")
class UserEntityTest {

    @Test
    @DisplayName("getAuthorities should return ROLE_ prefixed authority")
    void shouldReturnRolePrefixedAuthority() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@test.com")
                .passwordHash("hash")
                .role("ADMIN")
                .active(true)
                .build();

        assertThat(user.getAuthorities()).hasSize(1);
        assertThat(user.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("getPassword should return passwordHash")
    void shouldReturnPasswordHash() {
        User user = User.builder()
                .email("test@test.com")
                .passwordHash("$2a$10$hash")
                .role("USER")
                .build();

        assertThat(user.getPassword()).isEqualTo("$2a$10$hash");
    }

    @Test
    @DisplayName("getUsername should return email")
    void shouldReturnEmail() {
        User user = User.builder()
                .email("test@test.com")
                .passwordHash("hash")
                .role("USER")
                .build();

        assertThat(user.getUsername()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("isAccountNonLocked should reflect active status")
    void shouldReflectActiveStatus() {
        User active = User.builder().email("a@t.com").passwordHash("h").role("USER").active(true).build();
        User inactive = User.builder().email("b@t.com").passwordHash("h").role("USER").active(false).build();

        assertThat(active.isAccountNonLocked()).isTrue();
        assertThat(inactive.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("isEnabled should reflect active status")
    void shouldReflectEnabledStatus() {
        User active = User.builder().email("a@t.com").passwordHash("h").role("USER").active(true).build();
        User inactive = User.builder().email("b@t.com").passwordHash("h").role("USER").active(false).build();

        assertThat(active.isEnabled()).isTrue();
        assertThat(inactive.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isAccountNonExpired should always return true")
    void shouldAlwaysReturnNonExpired() {
        User user = User.builder().email("a@t.com").passwordHash("h").role("USER").build();
        assertThat(user.isAccountNonExpired()).isTrue();
    }

    @Test
    @DisplayName("isCredentialsNonExpired should always return true")
    void shouldAlwaysReturnCredentialsNonExpired() {
        User user = User.builder().email("a@t.com").passwordHash("h").role("USER").build();
        assertThat(user.isCredentialsNonExpired()).isTrue();
    }
}
