package com.pgcluster.api.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SshHostKey")
class SshHostKeyTest {

    @Test
    @DisplayName("onCreate should set firstSeenAt and lastVerifiedAt when null")
    void shouldSetTimestampsWhenNull() {
        SshHostKey key = SshHostKey.builder()
                .host("10.0.0.1")
                .fingerprint("SHA256:abc123")
                .build();

        key.onCreate();

        assertThat(key.getFirstSeenAt()).isNotNull();
        assertThat(key.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("onCreate should not overwrite existing firstSeenAt")
    void shouldNotOverwriteExistingFirstSeenAt() {
        Instant existingTime = Instant.parse("2025-01-01T00:00:00Z");
        SshHostKey key = SshHostKey.builder()
                .host("10.0.0.1")
                .fingerprint("SHA256:abc123")
                .firstSeenAt(existingTime)
                .build();

        key.onCreate();

        assertThat(key.getFirstSeenAt()).isEqualTo(existingTime);
        assertThat(key.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("onCreate should not overwrite existing lastVerifiedAt")
    void shouldNotOverwriteExistingLastVerifiedAt() {
        Instant existingTime = Instant.parse("2025-01-01T00:00:00Z");
        SshHostKey key = SshHostKey.builder()
                .host("10.0.0.1")
                .fingerprint("SHA256:abc123")
                .lastVerifiedAt(existingTime)
                .build();

        key.onCreate();

        assertThat(key.getFirstSeenAt()).isNotNull();
        assertThat(key.getLastVerifiedAt()).isEqualTo(existingTime);
    }
}
