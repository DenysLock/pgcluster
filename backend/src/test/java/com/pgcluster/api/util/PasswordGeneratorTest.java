package com.pgcluster.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordGenerator")
class PasswordGeneratorTest {

    @Test
    @DisplayName("generate should return alphanumeric password of requested length")
    void shouldGenerateAlphanumericPassword() {
        String password = PasswordGenerator.generate(16);
        assertThat(password).hasSize(16);
    }

    @Test
    @DisplayName("generate with special chars should include special characters")
    void shouldGeneratePasswordWithSpecialChars() {
        // Generate a long password to statistically guarantee special chars appear
        String password = PasswordGenerator.generate(200, true);
        assertThat(password).hasSize(200);
        assertThat(password).containsPattern("[!@#$%^&*]");
    }

    @Test
    @DisplayName("generate should throw for non-positive length")
    void shouldThrowForNonPositiveLength() {
        assertThatThrownBy(() -> PasswordGenerator.generate(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateDatabasePassword should return 24-character password")
    void shouldGenerateDatabasePassword() {
        String password = PasswordGenerator.generateDatabasePassword();
        assertThat(password).hasSize(24);
    }

    @Test
    @DisplayName("generateAlphanumericSuffix should return lowercase alphanumeric string")
    void shouldGenerateAlphanumericSuffix() {
        String suffix = PasswordGenerator.generateAlphanumericSuffix(8);
        assertThat(suffix).hasSize(8);
        assertThat(suffix).matches("[a-z0-9]+");
    }

    @Test
    @DisplayName("generateAlphanumericSuffix should throw for non-positive length")
    void shouldThrowSuffixForNonPositiveLength() {
        assertThatThrownBy(() -> PasswordGenerator.generateAlphanumericSuffix(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
