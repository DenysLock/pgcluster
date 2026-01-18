package com.pgcluster.api.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Utility class for secure random generation.
 * Provides thread-safe methods for generating passwords and other random values.
 */
@Component
public class SecureRandomUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Character set that avoids ambiguous characters (0/O, 1/l/I)
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final String ALPHANUMERIC_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Generate a secure random password.
     *
     * @param length the desired length of the password
     * @return a random password string
     */
    public String generatePassword(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Password length must be positive");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a random alphanumeric string.
     *
     * @param length the desired length
     * @return a random alphanumeric string
     */
    public String generateAlphanumeric(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC_CHARS.charAt(RANDOM.nextInt(ALPHANUMERIC_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a random slug suffix (4 digits).
     *
     * @return a 4-digit random string
     */
    public String generateSlugSuffix() {
        return String.format("%04d", RANDOM.nextInt(10000));
    }
}
