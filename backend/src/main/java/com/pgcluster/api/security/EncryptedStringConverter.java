package com.pgcluster.api.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for transparent field-level encryption.
 * Automatically encrypts when writing to database and decrypts when reading.
 *
 * Usage: Add @Convert(converter = EncryptedStringConverter.class) to entity fields.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static FieldEncryptor fieldEncryptor;

    @Autowired
    public void setFieldEncryptor(FieldEncryptor encryptor) {
        EncryptedStringConverter.fieldEncryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return fieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return fieldEncryptor.decrypt(dbData);
    }
}
