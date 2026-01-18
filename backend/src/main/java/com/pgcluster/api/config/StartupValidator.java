package com.pgcluster.api.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Validates critical configuration on application startup.
 * Fails fast if required configuration is missing or invalid.
 */
@Slf4j
@Component
public class StartupValidator {

    @Value("${ssh.private-key-path}")
    private String sshPrivateKeyPath;

    @Value("${cluster.base-domain}")
    private String clusterBaseDomain;

    @Value("${hetzner.api-token:}")
    private String hetznerApiToken;

    @Value("${cloudflare.api-token:}")
    private String cloudflareApiToken;

    @Value("${cloudflare.zone-id:}")
    private String cloudflareZoneId;

    @PostConstruct
    public void validate() {
        log.info("Validating startup configuration...");

        validateSshKey();
        validateClusterDomain();
        validateProvisioningConfig();

        log.info("Startup configuration validation complete");
    }

    private void validateSshKey() {
        if (sshPrivateKeyPath == null || sshPrivateKeyPath.isBlank()) {
            throw new IllegalStateException(
                    "SSH_PRIVATE_KEY_PATH must be configured for cluster provisioning");
        }

        File sshKeyFile = new File(sshPrivateKeyPath);
        if (!sshKeyFile.exists()) {
            log.warn("SSH private key file does not exist at: {}. " +
                    "Cluster provisioning will fail until this is configured.", sshPrivateKeyPath);
        } else if (!sshKeyFile.canRead()) {
            throw new IllegalStateException(
                    "SSH private key file exists but is not readable: " + sshPrivateKeyPath);
        } else {
            log.info("SSH private key validated: {}", sshPrivateKeyPath);
        }
    }

    private void validateClusterDomain() {
        if (clusterBaseDomain == null || clusterBaseDomain.isBlank()) {
            throw new IllegalStateException(
                    "CLUSTER_BASE_DOMAIN environment variable must be set");
        }

        if (!clusterBaseDomain.matches("^[a-zA-Z0-9][a-zA-Z0-9.-]+[a-zA-Z0-9]$")) {
            throw new IllegalStateException(
                    "CLUSTER_BASE_DOMAIN has invalid format: " + clusterBaseDomain);
        }

        log.info("Cluster base domain configured: {}", clusterBaseDomain);
    }

    private void validateProvisioningConfig() {
        boolean hetznerConfigured = hetznerApiToken != null && !hetznerApiToken.isBlank();
        boolean cloudflareConfigured = cloudflareApiToken != null && !cloudflareApiToken.isBlank()
                && cloudflareZoneId != null && !cloudflareZoneId.isBlank();

        if (!hetznerConfigured) {
            log.warn("Hetzner API token not configured. Cluster provisioning will be unavailable.");
        }

        if (!cloudflareConfigured) {
            log.warn("Cloudflare configuration incomplete. DNS management will be unavailable.");
        }

        if (hetznerConfigured && cloudflareConfigured) {
            log.info("Provisioning configuration validated - all external services configured");
        }
    }
}
