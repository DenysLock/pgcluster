package com.pgcluster.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("StartupValidator")
class StartupValidatorTest {

    @Nested
    @DisplayName("validateSshKey")
    class ValidateSshKey {

        @Test
        @DisplayName("should throw when ssh key path is blank")
        void shouldThrowWhenBlank() {
            StartupValidator validator = createValidator("", "db.pgcluster.com", "token", "cf-token", "zone-id");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SSH_PRIVATE_KEY_PATH");
        }

        @Test
        @DisplayName("should not throw when ssh key file exists and is readable")
        void shouldPassWhenFileExistsAndReadable(@TempDir Path tempDir) throws IOException {
            File sshKey = tempDir.resolve("id_rsa").toFile();
            sshKey.createNewFile();

            StartupValidator validator = createValidator(sshKey.getAbsolutePath(), "db.pgcluster.com", "token", "cf-token", "zone-id");

            assertThatNoException().isThrownBy(validator::validate);
        }

        @Test
        @DisplayName("should warn but not throw when ssh key file does not exist")
        void shouldWarnWhenFileNotFound() {
            StartupValidator validator = createValidator("/nonexistent/path/id_rsa", "db.pgcluster.com", "token", "cf-token", "zone-id");

            // Should not throw - just logs a warning
            assertThatNoException().isThrownBy(validator::validate);
        }
    }

    @Nested
    @DisplayName("validateClusterDomain")
    class ValidateClusterDomain {

        @Test
        @DisplayName("should throw when domain is blank")
        void shouldThrowWhenBlank(@TempDir Path tempDir) throws IOException {
            File sshKey = tempDir.resolve("id_rsa").toFile();
            sshKey.createNewFile();

            StartupValidator validator = createValidator(sshKey.getAbsolutePath(), "", "token", "cf-token", "zone-id");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CLUSTER_BASE_DOMAIN");
        }

        @Test
        @DisplayName("should throw when domain has invalid format")
        void shouldThrowWhenInvalidFormat(@TempDir Path tempDir) throws IOException {
            File sshKey = tempDir.resolve("id_rsa").toFile();
            sshKey.createNewFile();

            StartupValidator validator = createValidator(sshKey.getAbsolutePath(), "-invalid.com", "token", "cf-token", "zone-id");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("invalid format");
        }
    }

    @Nested
    @DisplayName("validateProvisioningConfig")
    class ValidateProvisioningConfig {

        @Test
        @DisplayName("should warn when hetzner token is missing")
        void shouldWarnWithoutHetzner(@TempDir Path tempDir) throws IOException {
            File sshKey = tempDir.resolve("id_rsa").toFile();
            sshKey.createNewFile();

            StartupValidator validator = createValidator(sshKey.getAbsolutePath(), "db.pgcluster.com", "", "cf-token", "zone-id");

            // Should complete without throwing - just logs warnings
            assertThatNoException().isThrownBy(validator::validate);
        }

        @Test
        @DisplayName("should warn when cloudflare config is incomplete")
        void shouldWarnWithoutCloudflare(@TempDir Path tempDir) throws IOException {
            File sshKey = tempDir.resolve("id_rsa").toFile();
            sshKey.createNewFile();

            StartupValidator validator = createValidator(sshKey.getAbsolutePath(), "db.pgcluster.com", "token", "", "");

            assertThatNoException().isThrownBy(validator::validate);
        }
    }

    private StartupValidator createValidator(String sshKeyPath, String domain, String hetznerToken, String cfToken, String cfZoneId) {
        StartupValidator validator = new StartupValidator();
        ReflectionTestUtils.setField(validator, "sshPrivateKeyPath", sshKeyPath);
        ReflectionTestUtils.setField(validator, "clusterBaseDomain", domain);
        ReflectionTestUtils.setField(validator, "hetznerApiToken", hetznerToken);
        ReflectionTestUtils.setField(validator, "cloudflareApiToken", cfToken);
        ReflectionTestUtils.setField(validator, "cloudflareZoneId", cfZoneId);
        return validator;
    }
}
