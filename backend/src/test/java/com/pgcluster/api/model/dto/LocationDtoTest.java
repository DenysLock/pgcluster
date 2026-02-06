package com.pgcluster.api.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocationDto")
class LocationDtoTest {

    @Nested
    @DisplayName("getFlag")
    class GetFlag {

        @Test
        @DisplayName("should return flag emoji for valid country code")
        void shouldReturnFlagForValidCode() {
            String flag = LocationDto.getFlag("DE");
            assertThat(flag).isNotNull().isNotEmpty();
            assertThat(flag).isNotEqualTo("\uD83C\uDF0D"); // not globe
        }

        @Test
        @DisplayName("should return globe for null country code")
        void shouldReturnGlobeForNull() {
            assertThat(LocationDto.getFlag(null)).isEqualTo("\uD83C\uDF0D");
        }

        @Test
        @DisplayName("should return globe for single char country code")
        void shouldReturnGlobeForSingleChar() {
            assertThat(LocationDto.getFlag("D")).isEqualTo("\uD83C\uDF0D");
        }

        @Test
        @DisplayName("should return globe for three char code")
        void shouldReturnGlobeForThreeChars() {
            assertThat(LocationDto.getFlag("DEU")).isEqualTo("\uD83C\uDF0D");
        }

        @Test
        @DisplayName("should handle lowercase input")
        void shouldHandleLowercase() {
            String upper = LocationDto.getFlag("DE");
            String lower = LocationDto.getFlag("de");
            assertThat(upper).isEqualTo(lower);
        }
    }

    @Nested
    @DisplayName("getCountryName")
    class GetCountryName {

        @Test
        @DisplayName("should return Germany for DE")
        void shouldReturnGermany() {
            assertThat(LocationDto.getCountryName("DE")).isEqualTo("Germany");
        }

        @Test
        @DisplayName("should return Finland for FI")
        void shouldReturnFinland() {
            assertThat(LocationDto.getCountryName("FI")).isEqualTo("Finland");
        }

        @Test
        @DisplayName("should return United States for US")
        void shouldReturnUS() {
            assertThat(LocationDto.getCountryName("US")).isEqualTo("United States");
        }

        @Test
        @DisplayName("should return Singapore for SG")
        void shouldReturnSingapore() {
            assertThat(LocationDto.getCountryName("SG")).isEqualTo("Singapore");
        }

        @Test
        @DisplayName("should return code itself for unknown country")
        void shouldReturnCodeForUnknown() {
            assertThat(LocationDto.getCountryName("XX")).isEqualTo("XX");
        }
    }

    @Nested
    @DisplayName("getCountryCodeForLocation")
    class GetCountryCodeForLocation {

        @Test
        @DisplayName("should return DE for fsn1")
        void shouldReturnDEForFsn1() {
            assertThat(LocationDto.getCountryCodeForLocation("fsn1")).isEqualTo("DE");
        }

        @Test
        @DisplayName("should return DE for nbg1")
        void shouldReturnDEForNbg1() {
            assertThat(LocationDto.getCountryCodeForLocation("nbg1")).isEqualTo("DE");
        }

        @Test
        @DisplayName("should return FI for hel1")
        void shouldReturnFIForHel1() {
            assertThat(LocationDto.getCountryCodeForLocation("hel1")).isEqualTo("FI");
        }

        @Test
        @DisplayName("should return US for ash")
        void shouldReturnUSForAsh() {
            assertThat(LocationDto.getCountryCodeForLocation("ash")).isEqualTo("US");
        }

        @Test
        @DisplayName("should return US for hil")
        void shouldReturnUSForHil() {
            assertThat(LocationDto.getCountryCodeForLocation("hil")).isEqualTo("US");
        }

        @Test
        @DisplayName("should return SG for sin")
        void shouldReturnSGForSin() {
            assertThat(LocationDto.getCountryCodeForLocation("sin")).isEqualTo("SG");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LocationDto.getCountryCodeForLocation(null)).isNull();
        }

        @Test
        @DisplayName("should return null for unknown location")
        void shouldReturnNullForUnknown() {
            assertThat(LocationDto.getCountryCodeForLocation("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("getFlagForLocation")
    class GetFlagForLocation {

        @Test
        @DisplayName("should return flag for known location")
        void shouldReturnFlagForKnownLocation() {
            String flag = LocationDto.getFlagForLocation("fsn1");
            assertThat(flag).isNotNull().isNotEqualTo("\uD83C\uDF0D");
        }

        @Test
        @DisplayName("should return globe for unknown location")
        void shouldReturnGlobeForUnknown() {
            assertThat(LocationDto.getFlagForLocation("unknown")).isEqualTo("\uD83C\uDF0D");
        }
    }
}
