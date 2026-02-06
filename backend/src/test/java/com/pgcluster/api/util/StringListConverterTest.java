package com.pgcluster.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StringListConverter")
class StringListConverterTest {

    private final StringListConverter converter = new StringListConverter();

    @Test
    @DisplayName("convertToDatabaseColumn should serialize list to JSON")
    void shouldSerializeListToJson() {
        List<String> list = List.of("a", "b", "c");
        String json = converter.convertToDatabaseColumn(list);
        assertThat(json).isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test
    @DisplayName("convertToDatabaseColumn should return null for null list")
    void shouldReturnNullForNullList() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToDatabaseColumn should return null for empty list")
    void shouldReturnNullForEmptyList() {
        assertThat(converter.convertToDatabaseColumn(Collections.emptyList())).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute should deserialize JSON to list")
    void shouldDeserializeJsonToList() {
        List<String> result = converter.convertToEntityAttribute("[\"x\",\"y\"]");
        assertThat(result).containsExactly("x", "y");
    }

    @Test
    @DisplayName("convertToEntityAttribute should return null for null data")
    void shouldReturnNullForNullData() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute should return null for invalid JSON")
    void shouldReturnNullForInvalidJson() {
        assertThat(converter.convertToEntityAttribute("not-json")).isNull();
    }
}
