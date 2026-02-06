package com.pgcluster.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("EncryptedStringConverter")
@ExtendWith(MockitoExtension.class)
class EncryptedStringConverterTest {

    @Mock
    private FieldEncryptor fieldEncryptor;

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
        converter.setFieldEncryptor(fieldEncryptor);
    }

    @Test
    @DisplayName("convertToDatabaseColumn should encrypt non-null value")
    void shouldEncryptValue() {
        when(fieldEncryptor.encrypt("secret")).thenReturn("encrypted-secret");

        String result = converter.convertToDatabaseColumn("secret");
        assertThat(result).isEqualTo("encrypted-secret");
    }

    @Test
    @DisplayName("convertToDatabaseColumn should return null for null input")
    void shouldReturnNullForNullEncrypt() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute should decrypt non-null value")
    void shouldDecryptValue() {
        when(fieldEncryptor.decrypt("encrypted-secret")).thenReturn("secret");

        String result = converter.convertToEntityAttribute("encrypted-secret");
        assertThat(result).isEqualTo("secret");
    }

    @Test
    @DisplayName("convertToEntityAttribute should return null for null input")
    void shouldReturnNullForNullDecrypt() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
