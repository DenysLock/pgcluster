package com.pgcluster.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecureRandomUtils")
class SecureRandomUtilsTest {

    private final SecureRandomUtils utils = new SecureRandomUtils();

    @Test
    @DisplayName("generatePassword should return string of requested length")
    void shouldGeneratePasswordOfCorrectLength() {
        String password = utils.generatePassword(16);
        assertThat(password).hasSize(16);
    }

    @Test
    @DisplayName("generatePassword should not contain ambiguous characters")
    void shouldNotContainAmbiguousChars() {
        String password = utils.generatePassword(100);
        assertThat(password).doesNotContain("0", "O", "1", "l", "I");
    }

    @Test
    @DisplayName("generatePassword should throw for non-positive length")
    void shouldThrowForNonPositiveLength() {
        assertThatThrownBy(() -> utils.generatePassword(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateAlphanumeric should return string of requested length")
    void shouldGenerateAlphanumericOfCorrectLength() {
        String result = utils.generateAlphanumeric(20);
        assertThat(result).hasSize(20);
        assertThat(result).matches("[A-Za-z0-9]+");
    }

    @Test
    @DisplayName("generateAlphanumeric should throw for non-positive length")
    void shouldThrowAlphanumericForNonPositiveLength() {
        assertThatThrownBy(() -> utils.generateAlphanumeric(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateSlugSuffix should return 4-digit string")
    void shouldGenerateSlugSuffix() {
        String suffix = utils.generateSlugSuffix();
        assertThat(suffix).hasSize(4);
        assertThat(suffix).matches("\\d{4}");
    }
}
