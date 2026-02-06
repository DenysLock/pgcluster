package com.pgcluster.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcluster.api.model.dto.ClusterCreateRequest;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.UserRepository;
import com.pgcluster.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Cluster Controller Validation")
class ClusterControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String userToken;

    // Valid node regions for testing
    private static final List<String> VALID_NODE_REGIONS = Arrays.asList("fsn1", "nbg1", "hel1");

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@test.com")
                .passwordHash("$2a$10$dummy")
                .role("USER")
                .active(true)
                .build();
        user = userRepository.save(user);

        userToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole(), true);
    }

    @Nested
    @DisplayName("POST /api/v1/clusters - Name Validation")
    class NameValidation {

        @Test
        @DisplayName("should reject empty name")
        void shouldRejectEmptyName() throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("");
            request.setNodeSize("cx23");
            request.setNodeRegions(VALID_NODE_REGIONS);

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));
        }

        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() throws Exception {
            String json = """
                {
                    "nodeSize": "cx23",
                    "nodeRegions": ["fsn1", "nbg1", "hel1"]
                }
                """;

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));
        }

        @Test
        @DisplayName("should reject name exceeding max length")
        void shouldRejectNameExceedingMaxLength() throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("a".repeat(51)); // 51 chars, max is 50
            request.setNodeSize("cx23");
            request.setNodeRegions(VALID_NODE_REGIONS);

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clusters - Node Regions Validation")
    class NodeRegionsValidation {

        @Test
        @DisplayName("should reject null node regions")
        void shouldRejectNullNodeRegions() throws Exception {
            String json = """
                {
                    "name": "Test Cluster",
                    "nodeSize": "cx23"
                }
                """;

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.nodeRegions", notNullValue()));
        }

        @Test
        @DisplayName("should reject 2 node regions (only 1 or 3 allowed)")
        void shouldRejectTwoNodeRegions() throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("Test Cluster");
            request.setNodeSize("cx23");
            request.setNodeRegions(Arrays.asList("fsn1", "nbg1")); // 2 is invalid (must be 1 or 3)

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.validNodeCount", notNullValue()));
        }

        @Test
        @DisplayName("should reject more than 3 node regions")
        void shouldRejectMoreThan3NodeRegions() throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("Test Cluster");
            request.setNodeSize("cx23");
            request.setNodeRegions(Arrays.asList("fsn1", "nbg1", "hel1", "ash")); // 4

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.nodeRegions", notNullValue()));
        }

        @Test
        @DisplayName("should accept exactly 3 node regions (passes validation)")
        void shouldAcceptExactly3NodeRegions() throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("Test Cluster");
            request.setNodeSize("cx23");
            request.setNodeRegions(Arrays.asList("fsn1", "nbg1", "hel1"));

            MvcResult result = mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // Valid input should not return 400 Bad Request (validation passed)
            assertThat(result.getResponse().getStatus()).isNotEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clusters - Node Size Validation")
    class NodeSizeValidation {

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "cx99", "small", "large", "xlarge"})
        @DisplayName("should reject invalid node sizes")
        void shouldRejectInvalidNodeSizes(String nodeSize) throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("Test Cluster");
            request.setNodeSize(nodeSize);
            request.setNodeRegions(VALID_NODE_REGIONS);

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.nodeSize", notNullValue()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"cx23", "cx33", "cx43", "cx53", "cpx11", "cpx21", "cpx31", "cpx41", "cpx51"})
        @DisplayName("should accept valid node sizes (passes validation)")
        void shouldAcceptValidNodeSizes(String nodeSize) throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("Test Cluster " + nodeSize);
            request.setNodeSize(nodeSize);
            request.setNodeRegions(VALID_NODE_REGIONS);

            MvcResult result = mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isNotEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clusters - Postgres Version Validation")
    class PostgresVersionValidation {

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "9", "10", "13", "18"})
        @DisplayName("should reject invalid postgres versions")
        void shouldRejectInvalidVersions(String version) throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("Test Cluster");
            request.setNodeSize("cx23");
            request.setNodeRegions(VALID_NODE_REGIONS);
            request.setPostgresVersion(version);

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.postgresVersion", notNullValue()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"14", "15", "16", "17"})
        @DisplayName("should accept valid postgres versions (passes validation)")
        void shouldAcceptValidVersions(String version) throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName("Test Cluster v" + version);
            request.setNodeSize("cx23");
            request.setNodeRegions(VALID_NODE_REGIONS);
            request.setPostgresVersion(version);

            MvcResult result = mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isNotEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clusters - Multiple Validation Errors")
    class MultipleValidationErrors {

        @Test
        @DisplayName("should return all validation errors at once")
        void shouldReturnAllValidationErrors() throws Exception {
            ClusterCreateRequest request = new ClusterCreateRequest();
            request.setName(""); // invalid
            request.setNodeSize("invalid"); // invalid
            request.setNodeRegions(Arrays.asList("fsn1", "nbg1")); // invalid - 2 nodes not allowed (must be 1 or 3)

            mockMvc.perform(post("/api/v1/clusters")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", aMapWithSize(greaterThanOrEqualTo(3))));
        }
    }
}
