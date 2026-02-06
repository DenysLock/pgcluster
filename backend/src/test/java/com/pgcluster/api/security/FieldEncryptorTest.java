package com.pgcluster.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldEncryptor")
class FieldEncryptorTest {

    private FieldEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new FieldEncryptor();
        // Set a valid 32-byte base64-encoded key
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) (i + 1);
        String base64Key = Base64.getEncoder().encodeToString(key);
        ReflectionTestUtils.setField(encryptor, "base64Key", base64Key);
        encryptor.init();
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("should throw when key is blank")
        void shouldThrowWhenKeyBlank() {
            FieldEncryptor e = new FieldEncryptor();
            ReflectionTestUtils.setField(e, "base64Key", "");
            assertThatThrownBy(e::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIELD_ENCRYPTION_KEY");
        }

        @Test
        @DisplayName("should throw when key is not 32 bytes")
        void shouldThrowWhenKeyWrongLength() {
            FieldEncryptor e = new FieldEncryptor();
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
            ReflectionTestUtils.setField(e, "base64Key", shortKey);
            assertThatThrownBy(e::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("should throw when key is invalid base64")
        void shouldThrowWhenKeyInvalidBase64() {
            FieldEncryptor e = new FieldEncryptor();
            ReflectionTestUtils.setField(e, "base64Key", "not-valid-base64!!!");
            assertThatThrownBy(e::init)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("encrypt/decrypt")
    class EncryptDecrypt {

        @Test
        @DisplayName("should round-trip encrypt and decrypt correctly")
        void shouldRoundTrip() {
            String plaintext = "my-secret-password";
            String encrypted = encryptor.encrypt(plaintext);
            String decrypted = encryptor.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should return null for null encrypt input")
        void shouldReturnNullForNullEncrypt() {
            assertThat(encryptor.encrypt(null)).isNull();
        }

        @Test
        @DisplayName("should return null for null decrypt input")
        void shouldReturnNullForNullDecrypt() {
            assertThat(encryptor.decrypt(null)).isNull();
        }

        @Test
        @DisplayName("should produce different ciphertext for same plaintext (unique IV)")
        void shouldProduceDifferentCiphertext() {
            String plaintext = "same-input";
            String encrypted1 = encryptor.encrypt(plaintext);
            String encrypted2 = encryptor.encrypt(plaintext);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }

        @Test
        @DisplayName("should handle unicode characters")
        void shouldHandleUnicode() {
            String plaintext = "пароль-日本語-emoji-🔑";
            String encrypted = encryptor.encrypt(plaintext);
            String decrypted = encryptor.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() {
            String encrypted = encryptor.encrypt("");
            String decrypted = encryptor.decrypt(encrypted);

            assertThat(decrypted).isEqualTo("");
        }

        @Test
        @DisplayName("should throw for tampered ciphertext")
        void shouldThrowForTamperedCiphertext() {
            String encrypted = encryptor.encrypt("test");
            // Tamper with the ciphertext
            String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

            assertThatThrownBy(() -> encryptor.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
