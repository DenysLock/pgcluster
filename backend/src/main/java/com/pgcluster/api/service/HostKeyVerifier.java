package com.pgcluster.api.service;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.UserInfo;
import com.pgcluster.api.model.entity.SshHostKey;
import com.pgcluster.api.repository.SshHostKeyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trust-On-First-Use (TOFU) host key verifier for SSH connections.
 *
 * Fingerprints are persisted to the database to survive application restarts.
 * An in-memory cache is used for fast lookups during SSH connections.
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
@RequiredArgsConstructor
public class HostKeyVerifier implements HostKeyRepository {

    private final SshHostKeyRepository sshHostKeyRepository;

    // Self-injection for @Transactional to work on internal calls
    @Autowired
    @Lazy
    private HostKeyVerifier self;

    // In-memory cache of trusted host key fingerprints for fast lookups
    // Key: hostname or IP, Value: SHA-256 fingerprint of public key
    private final Map<String, String> fingerprintCache = new ConcurrentHashMap<>();

    /**
     * Load existing host keys from database into cache on startup.
     */
    @PostConstruct
    public void loadFromDatabase() {
        try {
            sshHostKeyRepository.findAll().forEach(key ->
                fingerprintCache.put(key.getHost(), key.getFingerprint())
            );
            log.info("Loaded {} SSH host keys from database", fingerprintCache.size());
        } catch (Exception e) {
            log.warn("Failed to load SSH host keys from database: {}", e.getMessage());
        }
    }

    @Override
    public int check(String host, byte[] key) {
        String fingerprint = computeFingerprint(key);

        // Check cache first
        String cachedFingerprint = fingerprintCache.get(host);

        if (cachedFingerprint == null) {
            // Not in cache - check database (might have been added by another instance)
            cachedFingerprint = sshHostKeyRepository.findByHost(host)
                    .map(SshHostKey::getFingerprint)
                    .orElse(null);

            if (cachedFingerprint != null) {
                // Found in DB, add to cache
                fingerprintCache.put(host, cachedFingerprint);
            }
        }

        if (cachedFingerprint == null) {
            // First connection to this host - trust on first use
            log.info("TOFU: Trusting new SSH host key for {}: {}", host, fingerprint);
            self.saveHostKey(host, fingerprint, null);
            return OK;
        }

        if (cachedFingerprint.equals(fingerprint)) {
            log.debug("SSH host key verified for {}", host);
            self.updateLastVerified(host);
            return OK;
        }

        // Key mismatch - potential MITM attack!
        log.error("SSH HOST KEY MISMATCH for {}! Expected: {}, Got: {}. " +
                "This could indicate a man-in-the-middle attack.",
                host, cachedFingerprint, fingerprint);
        return NOT_INCLUDED;
    }

    @Override
    public void add(HostKey hostkey, UserInfo ui) {
        String host = hostkey.getHost();
        String keyType = hostkey.getType();
        // hostkey.getKey() returns base64-encoded key string, decode it first
        String fingerprint = computeFingerprint(Base64.getDecoder().decode(hostkey.getKey()));
        self.saveHostKey(host, fingerprint, keyType);
        log.info("Added trusted host key for {}: {}", host, fingerprint);
    }

    @Override
    public void remove(String host, String type) {
        self.removeHostKey(host);
        log.info("Removed trusted host key for {}", host);
    }

    @Override
    public void remove(String host, String type, byte[] key) {
        self.removeHostKey(host);
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
     * Remove trust for a host. Call this when a server is being deleted
     * or when an IP might be recycled.
     */
    @Transactional
    public void removeHost(String host) {
        try {
            sshHostKeyRepository.deleteByHost(host);
        } catch (Exception e) {
            log.debug("No host key to delete for {}: {}", host, e.getMessage());
        }
        fingerprintCache.remove(host);
        log.info("Removed trust for host {}", host);
    }

    /**
     * Save a host key to the database and cache.
     */
    @Transactional
    public void saveHostKey(String host, String fingerprint, String keyType) {
        try {
            SshHostKey hostKey = sshHostKeyRepository.findByHost(host)
                    .map(existing -> {
                        existing.setFingerprint(fingerprint);
                        existing.setKeyType(keyType);
                        existing.setLastVerifiedAt(Instant.now());
                        return existing;
                    })
                    .orElseGet(() -> SshHostKey.builder()
                            .host(host)
                            .fingerprint(fingerprint)
                            .keyType(keyType)
                            .firstSeenAt(Instant.now())
                            .lastVerifiedAt(Instant.now())
                            .build());

            sshHostKeyRepository.save(hostKey);
            fingerprintCache.put(host, fingerprint);
        } catch (Exception e) {
            log.error("Failed to save host key for {}: {}", host, e.getMessage());
            // Still update cache even if DB save fails
            fingerprintCache.put(host, fingerprint);
        }
    }

    /**
     * Remove a host key from the database and cache.
     */
    @Transactional
    public void removeHostKey(String host) {
        try {
            sshHostKeyRepository.deleteByHost(host);
        } catch (Exception e) {
            log.debug("No host key to delete for {}: {}", host, e.getMessage());
        }
        fingerprintCache.remove(host);
    }

    /**
     * Update the last verified timestamp for a host.
     */
    @Transactional
    public void updateLastVerified(String host) {
        try {
            sshHostKeyRepository.updateLastVerifiedAt(host, Instant.now());
        } catch (Exception e) {
            log.debug("Failed to update last verified timestamp for {}: {}", host, e.getMessage());
        }
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
