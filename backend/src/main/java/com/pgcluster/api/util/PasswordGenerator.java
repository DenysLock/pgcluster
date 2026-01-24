package com.pgcluster.api.util;

import java.security.SecureRandom;

/**
 * Utility class for generating secure random passwords.
 * Uses SecureRandom for cryptographically strong random generation.
 */
public final class PasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Characters used for password generation.
     * Excludes ambiguous characters: 0, O, I, l, 1
     */
    private static final String ALPHANUMERIC_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    /**
     * Special characters for more complex passwords.
     */
    private static final String SPECIAL_CHARS = "!@#$%^&*";

    private PasswordGenerator() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate a random alphanumeric password.
     *
     * @param length The desired password length
     * @return A random password string
     */
    public static String generate(int length) {
        return generate(length, false);
    }

    /**
     * Generate a random password with optional special characters.
     *
     * @param length The desired password length
     * @param includeSpecialChars Whether to include special characters
     * @return A random password string
     */
    public static String generate(int length, boolean includeSpecialChars) {
        if (length <= 0) {
            throw new IllegalArgumentException("Password length must be positive");
        }

        String chars = includeSpecialChars
                ? ALPHANUMERIC_CHARS + SPECIAL_CHARS
                : ALPHANUMERIC_CHARS;

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a secure password suitable for database credentials.
     * Uses 24 characters by default.
     *
     * @return A random 24-character password
     */
    public static String generateDatabasePassword() {
        return generate(24);
    }

    /**
     * Generate a random lowercase alphanumeric string.
     * Useful for unique identifiers and slug suffixes.
     *
     * @param length The desired string length
     * @return A random lowercase alphanumeric string
     */
    public static String generateAlphanumericSuffix(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }

        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
