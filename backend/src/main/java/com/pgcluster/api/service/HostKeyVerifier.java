package com.pgcluster.api.service;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trust-On-First-Use (TOFU) host key verifier for SSH connections.
 *
 * When connecting to a new host:
 * - First connection: Accept and store the host key fingerprint
 * - Subsequent connections: Verify the key matches the stored fingerprint
 *
 * This is more secure than StrictHostKeyChecking=no while being practical
 * for automated server provisioning where hosts are dynamically created.
 */
@Slf4j
@Component
public class HostKeyVerifier implements HostKeyRepository {

    // In-memory store of trusted host key fingerprints
    // Key: hostname or IP, Value: SHA-256 fingerprint of public key
    private final Map<String, String> trustedFingerprints = new ConcurrentHashMap<>();

    @Override
    public int check(String host, byte[] key) {
        String fingerprint = computeFingerprint(key);
        String storedFingerprint = trustedFingerprints.get(host);

        if (storedFingerprint == null) {
            // First connection to this host - trust on first use
            log.info("TOFU: Trusting new SSH host key for {}: {}", host, fingerprint);
            trustedFingerprints.put(host, fingerprint);
            return OK;
        }

        if (storedFingerprint.equals(fingerprint)) {
            log.debug("SSH host key verified for {}", host);
            return OK;
        }

        // Key mismatch - potential MITM attack!
        log.error("SSH HOST KEY MISMATCH for {}! Expected: {}, Got: {}. " +
                "This could indicate a man-in-the-middle attack.",
                host, storedFingerprint, fingerprint);
        return NOT_INCLUDED;
    }

    @Override
    public void add(HostKey hostkey, UserInfo ui) {
        String host = hostkey.getHost();
        // hostkey.getKey() returns base64-encoded key string, decode it first
        String fingerprint = computeFingerprint(Base64.getDecoder().decode(hostkey.getKey()));
        trustedFingerprints.put(host, fingerprint);
        log.info("Added trusted host key for {}: {}", host, fingerprint);
    }

    @Override
    public void remove(String host, String type) {
        trustedFingerprints.remove(host);
        log.info("Removed trusted host key for {}", host);
    }

    @Override
    public void remove(String host, String type, byte[] key) {
        trustedFingerprints.remove(host);
        log.info("Removed trusted host key for {}", host);
    }

    @Override
    public String getKnownHostsRepositoryID() {
        return "pgcluster-tofu-repository";
    }

    @Override
    public HostKey[] getHostKey() {
        return new HostKey[0];
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        return new HostKey[0];
    }

    /**
     * Remove trust for a host. Call this when a server is being deleted.
     */
    public void removeHost(String host) {
        trustedFingerprints.remove(host);
        log.info("Removed trust for host {}", host);
    }

    /**
     * Compute SHA-256 fingerprint of a public key.
     */
    private String computeFingerprint(byte[] key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to compute key fingerprint", e);
            return "";
        }
    }
}
