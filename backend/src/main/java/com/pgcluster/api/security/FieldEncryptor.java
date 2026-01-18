package com.pgcluster.api.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field-level encryption for sensitive data like passwords.
 * Each encryption operation uses a unique IV for security.
 */
@Slf4j
@Component
public class FieldEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

    @Value("${security.field-encryption-key:}")
    private String base64Key;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                "FIELD_ENCRYPTION_KEY environment variable must be set. " +
                "Generate a base64-encoded 32-byte key using: " +
                "openssl rand -base64 32"
            );
        }

        try {
            byte[] decodedKey = Base64.getDecoder().decode(base64Key);
            if (decodedKey.length != 32) {
                throw new IllegalStateException(
                    "FIELD_ENCRYPTION_KEY must be exactly 32 bytes (256 bits) when decoded. " +
                    "Current length: " + decodedKey.length + " bytes"
                );
            }
            this.secretKey = new SecretKeySpec(decodedKey, "AES");
            log.info("Field encryptor initialized successfully");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "FIELD_ENCRYPTION_KEY must be valid base64 encoding", e
            );
        }
    }

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     * Format: base64(IV || ciphertext || auth_tag)
     *
     * @param plaintext the string to encrypt
     * @return base64-encoded encrypted data, or null if input is null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt field", e);
        }
    }

    /**
     * Decrypts a base64-encoded AES-256-GCM encrypted string.
     *
     * @param encryptedData base64-encoded encrypted data
     * @return the decrypted plaintext, or null if input is null
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt field", e);
        }
    }
}
