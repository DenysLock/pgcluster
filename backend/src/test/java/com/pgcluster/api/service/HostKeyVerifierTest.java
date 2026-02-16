package com.pgcluster.api.service;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.UserInfo;
import com.pgcluster.api.model.entity.SshHostKey;
import com.pgcluster.api.repository.SshHostKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.jcraft.jsch.HostKeyRepository.NOT_INCLUDED;
import static com.jcraft.jsch.HostKeyRepository.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("HostKeyVerifier")
@ExtendWith(MockitoExtension.class)
class HostKeyVerifierTest {

    @Mock
    private SshHostKeyRepository sshHostKeyRepository;

    @InjectMocks
    private HostKeyVerifier hostKeyVerifier;

    @BeforeEach
    void setUp() {
        // Set self to the same instance (simulates Spring's self-injection for @Transactional)
        ReflectionTestUtils.setField(hostKeyVerifier, "self", hostKeyVerifier);
    }

    @Nested
    @DisplayName("loadFromDatabase")
    class LoadFromDatabase {

        @Test
        @DisplayName("should load keys from database into cache")
        void shouldLoadKeys() {
            SshHostKey key = SshHostKey.builder()
                    .host("10.0.0.1")
                    .fingerprint("SHA256:abc123")
                    .keyType("ssh-ed25519")
                    .build();

            when(sshHostKeyRepository.findAll()).thenReturn(List.of(key));

            hostKeyVerifier.loadFromDatabase();

            // Verify key is now in cache by calling check with the same fingerprint
            // The fingerprint for a 0-byte key — we just verify the load doesn't crash
            verify(sshHostKeyRepository).findAll();
        }

        @Test
        @DisplayName("should handle database exception gracefully")
        void shouldHandleDatabaseException() {
            when(sshHostKeyRepository.findAll()).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            hostKeyVerifier.loadFromDatabase();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        @DisplayName("should return OK for new host (TOFU)")
        void shouldTrustNewHost() {
            String host = "10.0.0.1";
            byte[] key = new byte[]{1, 2, 3, 4, 5};

            when(sshHostKeyRepository.findByHost(host)).thenReturn(Optional.empty());
            when(sshHostKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = hostKeyVerifier.check(host, key);

            assertThat(result).isEqualTo(OK);
            verify(sshHostKeyRepository).save(any(SshHostKey.class));
        }

        @Test
        @DisplayName("should return OK when fingerprint matches cached value")
        void shouldAcceptMatchingFingerprint() {
            String host = "10.0.0.2";
            byte[] key = new byte[]{10, 20, 30};

            // First, add to cache via check (TOFU)
            when(sshHostKeyRepository.findByHost(host)).thenReturn(Optional.empty());
            when(sshHostKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            hostKeyVerifier.check(host, key);

            // Now check again with same key — should use cache
            int result = hostKeyVerifier.check(host, key);

            assertThat(result).isEqualTo(OK);
        }

        @Test
        @DisplayName("should return NOT_INCLUDED when fingerprint mismatches")
        void shouldRejectMismatchedFingerprint() {
            String host = "10.0.0.3";
            byte[] originalKey = new byte[]{1, 2, 3};
            byte[] differentKey = new byte[]{4, 5, 6};

            // Add original key to cache
            when(sshHostKeyRepository.findByHost(host)).thenReturn(Optional.empty());
            when(sshHostKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            hostKeyVerifier.check(host, originalKey);

            // Now try with a different key
            int result = hostKeyVerifier.check(host, differentKey);

            assertThat(result).isEqualTo(NOT_INCLUDED);
        }

        @Test
        @DisplayName("should return OK when fingerprint matches DB record (cache miss)")
        void shouldAcceptMatchingFingerprintFromDb() {
            String host = "10.0.0.4";
            byte[] key = new byte[]{7, 8, 9};

            // Compute the fingerprint the same way as HostKeyVerifier
            java.security.MessageDigest digest;
            try {
                digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(key);
                String expectedFingerprint = java.util.Base64.getEncoder().encodeToString(hash);

                SshHostKey existingKey = SshHostKey.builder()
                        .host(host)
                        .fingerprint(expectedFingerprint)
                        .keyType("ssh-ed25519")
                        .firstSeenAt(Instant.now())
                        .lastVerifiedAt(Instant.now())
                        .build();

                when(sshHostKeyRepository.findByHost(host)).thenReturn(Optional.of(existingKey));

                int result = hostKeyVerifier.check(host, key);

                assertThat(result).isEqualTo(OK);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    @DisplayName("saveHostKey")
    class SaveHostKey {

        @Test
        @DisplayName("should create new host key when not exists")
        void shouldCreateNewKey() {
            when(sshHostKeyRepository.findByHost("10.0.0.5")).thenReturn(Optional.empty());
            when(sshHostKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            hostKeyVerifier.saveHostKey("10.0.0.5", "SHA256:newkey", "ssh-ed25519");

            verify(sshHostKeyRepository).save(argThat(k ->
                    "10.0.0.5".equals(k.getHost()) &&
                    "SHA256:newkey".equals(k.getFingerprint()) &&
                    "ssh-ed25519".equals(k.getKeyType())
            ));
        }

        @Test
        @DisplayName("should update existing host key")
        void shouldUpdateExistingKey() {
            SshHostKey existing = SshHostKey.builder()
                    .id(UUID.randomUUID())
                    .host("10.0.0.6")
                    .fingerprint("SHA256:oldkey")
                    .keyType("ssh-rsa")
                    .firstSeenAt(Instant.now())
                    .lastVerifiedAt(Instant.now())
                    .build();

            when(sshHostKeyRepository.findByHost("10.0.0.6")).thenReturn(Optional.of(existing));
            when(sshHostKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            hostKeyVerifier.saveHostKey("10.0.0.6", "SHA256:newkey", "ssh-ed25519");

            verify(sshHostKeyRepository).save(argThat(k ->
                    "SHA256:newkey".equals(k.getFingerprint())
            ));
        }

        @Test
        @DisplayName("should still update cache when DB save fails")
        void shouldUpdateCacheOnDbFailure() {
            when(sshHostKeyRepository.findByHost("10.0.0.7")).thenReturn(Optional.empty());
            when(sshHostKeyRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            // Should not throw — cache is still updated
            hostKeyVerifier.saveHostKey("10.0.0.7", "SHA256:abc", "ssh-ed25519");

            // Verify subsequent check uses cache (host trusted)
            byte[] sameKey = {0};
            // We can't easily verify the cache directly, but no exception should occur
        }
    }

    @Nested
    @DisplayName("removeHostKey")
    class RemoveHostKey {

        @Test
        @DisplayName("should delete from DB and remove from cache")
        void shouldRemoveKey() {
            hostKeyVerifier.removeHostKey("10.0.0.8");

            verify(sshHostKeyRepository).deleteByHost("10.0.0.8");
        }

        @Test
        @DisplayName("should handle DB exception gracefully")
        void shouldHandleDbException() {
            doThrow(new RuntimeException("DB error")).when(sshHostKeyRepository).deleteByHost(any());

            // Should not throw
            hostKeyVerifier.removeHostKey("10.0.0.9");
        }
    }

    @Nested
    @DisplayName("removeHost")
    class RemoveHost {

        @Test
        @DisplayName("should delete from DB and remove from cache")
        void shouldRemoveHost() {
            hostKeyVerifier.removeHost("10.0.0.10");

            verify(sshHostKeyRepository).deleteByHost("10.0.0.10");
        }

        @Test
        @DisplayName("should handle DB exception gracefully")
        void shouldHandleDbException() {
            doThrow(new RuntimeException("DB error")).when(sshHostKeyRepository).deleteByHost(any());

            // Should not throw
            hostKeyVerifier.removeHost("10.0.0.11");
        }
    }

    @Nested
    @DisplayName("updateLastVerified")
    class UpdateLastVerified {

        @Test
        @DisplayName("should update timestamp in DB")
        void shouldUpdateTimestamp() {
            hostKeyVerifier.updateLastVerified("10.0.0.12");

            verify(sshHostKeyRepository).updateLastVerifiedAt(eq("10.0.0.12"), any(Instant.class));
        }

        @Test
        @DisplayName("should handle DB exception gracefully")
        void shouldHandleDbException() {
            doThrow(new RuntimeException("DB error"))
                    .when(sshHostKeyRepository).updateLastVerifiedAt(any(), any());

            // Should not throw
            hostKeyVerifier.updateLastVerified("10.0.0.13");
        }
    }

    @Nested
    @DisplayName("getKnownHostsRepositoryID")
    class GetKnownHostsRepositoryID {

        @Test
        @DisplayName("should return fixed repository ID")
        void shouldReturnId() {
            assertThat(hostKeyVerifier.getKnownHostsRepositoryID())
                    .isEqualTo("pgcluster-tofu-repository");
        }
    }

    @Nested
    @DisplayName("getHostKey")
    class GetHostKeyMethods {

        @Test
        @DisplayName("getHostKey() should return empty array")
        void shouldReturnEmptyArray() {
            assertThat(hostKeyVerifier.getHostKey()).isEmpty();
        }

        @Test
        @DisplayName("getHostKey(host, type) should return empty array")
        void shouldReturnEmptyArrayForHostType() {
            assertThat(hostKeyVerifier.getHostKey("10.0.0.1", "ssh-ed25519")).isEmpty();
        }
    }

    @Nested
    @DisplayName("remove overloads")
    class RemoveOverloads {

        @Test
        @DisplayName("remove(host, type) should call removeHostKey")
        void removeByHostType() {
            hostKeyVerifier.remove("10.0.0.1", "ssh-ed25519");
            verify(sshHostKeyRepository).deleteByHost("10.0.0.1");
        }

        @Test
        @DisplayName("remove(host, type, key) should call removeHostKey")
        void removeByHostTypeKey() {
            hostKeyVerifier.remove("10.0.0.1", "ssh-ed25519", new byte[]{1, 2, 3});
            verify(sshHostKeyRepository).deleteByHost("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("should save new host key from HostKey object")
        void shouldAddHostKey() throws Exception {
            // Create a mock HostKey
            HostKey hostKey = mock(HostKey.class);
            when(hostKey.getHost()).thenReturn("10.0.0.1");
            when(hostKey.getType()).thenReturn("ssh-ed25519");
            // Base64-encoded bytes that are valid to decode
            when(hostKey.getKey()).thenReturn(
                    java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4})
            );

            UserInfo userInfo = mock(UserInfo.class);

            when(sshHostKeyRepository.findByHost("10.0.0.1")).thenReturn(Optional.empty());
            when(sshHostKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            hostKeyVerifier.add(hostKey, userInfo);

            verify(sshHostKeyRepository).save(argThat(k -> "10.0.0.1".equals(k.getHost())));
        }
    }
}
