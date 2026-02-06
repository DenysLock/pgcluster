package com.pgcluster.api.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RestoreRequest")
class RestoreRequestTest {

    @Test
    @DisplayName("isValidNodeCount should accept null regions")
    void shouldAcceptNullRegions() {
        RestoreRequest request = RestoreRequest.builder().nodeRegions(null).build();
        assertThat(request.isValidNodeCount()).isTrue();
    }

    @Test
    @DisplayName("isValidNodeCount should accept 1 region")
    void shouldAcceptOneRegion() {
        RestoreRequest request = RestoreRequest.builder().nodeRegions(List.of("fsn1")).build();
        assertThat(request.isValidNodeCount()).isTrue();
    }

    @Test
    @DisplayName("isValidNodeCount should accept 3 regions")
    void shouldAcceptThreeRegions() {
        RestoreRequest request = RestoreRequest.builder().nodeRegions(List.of("fsn1", "nbg1", "hel1")).build();
        assertThat(request.isValidNodeCount()).isTrue();
    }

    @Test
    @DisplayName("isValidNodeCount should reject 2 regions")
    void shouldRejectTwoRegions() {
        RestoreRequest request = RestoreRequest.builder().nodeRegions(List.of("fsn1", "nbg1")).build();
        assertThat(request.isValidNodeCount()).isFalse();
    }
}
