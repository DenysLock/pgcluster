package com.pgcluster.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.UserRepository;
import com.pgcluster.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Cluster Controller Authorization")
class ClusterControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user1;
    private User user2;
    private String user1Token;
    private String user2Token;
    private Cluster user1Cluster;

    @BeforeEach
    void setUp() {
        // Create two test users
        user1 = User.builder()
                .email("user1@test.com")
                .passwordHash("$2a$10$dummy")
                .role("USER")
                .active(true)
                .build();
        user1 = userRepository.save(user1);

        user2 = User.builder()
                .email("user2@test.com")
                .passwordHash("$2a$10$dummy")
                .role("USER")
                .active(true)
                .build();
        user2 = userRepository.save(user2);

        // Generate tokens
        user1Token = jwtTokenProvider.generateToken(user1.getId(), user1.getEmail(), user1.getRole(), true);
        user2Token = jwtTokenProvider.generateToken(user2.getId(), user2.getEmail(), user2.getRole(), true);

        // Create a cluster owned by user1
        user1Cluster = Cluster.builder()
                .user(user1)
                .name("User1 Cluster")
                .slug("user1-cluster-1234")
                .plan("starter")
                .status("running")
                .postgresVersion("16")
                .nodeCount(3)
                .nodeSize("cx23")
                .region("fsn1")
                .hostname("user1-cluster-1234.test.pgcluster.local")
                .port(5432)
                .postgresPassword("test-password-123")
                .build();
        user1Cluster = clusterRepository.save(user1Cluster);
    }

    @Nested
    @DisplayName("GET /api/v1/clusters")
    class ListClusters {

        @Test
        @DisplayName("user should only see their own clusters")
        void shouldOnlySeeOwnClusters() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer " + user1Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clusters", hasSize(1)))
                    .andExpect(jsonPath("$.clusters[0].slug", is("user1-cluster-1234")));
        }

        @Test
        @DisplayName("user2 should not see user1's clusters")
        void shouldNotSeeOtherUsersClusters() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer " + user2Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clusters", hasSize(0)));
        }

        @Test
        @DisplayName("should reject request without authentication")
        void shouldReturn401WithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/clusters"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{id}")
    class GetCluster {

        @Test
        @DisplayName("owner can access their cluster")
        void ownerCanAccessCluster() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + user1Cluster.getId())
                            .header("Authorization", "Bearer " + user1Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug", is("user1-cluster-1234")));
        }

        @Test
        @DisplayName("other user cannot access cluster (returns 404)")
        void otherUserCannotAccessCluster() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + user1Cluster.getId())
                            .header("Authorization", "Bearer " + user2Token))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for non-existent cluster")
        void shouldReturn404ForNonExistentCluster() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + user1Token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{id}/credentials")
    class GetCredentials {

        @Test
        @DisplayName("owner can access credentials")
        void ownerCanAccessCredentials() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + user1Cluster.getId() + "/credentials")
                            .header("Authorization", "Bearer " + user1Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.password", is("test-password-123")))
                    .andExpect(jsonPath("$.warning", notNullValue()));
        }

        @Test
        @DisplayName("other user cannot access credentials (returns 404)")
        void otherUserCannotAccessCredentials() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + user1Cluster.getId() + "/credentials")
                            .header("Authorization", "Bearer " + user2Token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/clusters/{id}")
    class DeleteCluster {

        @Test
        @DisplayName("other user cannot delete cluster (returns 404)")
        void otherUserCannotDeleteCluster() throws Exception {
            mockMvc.perform(delete("/api/v1/clusters/" + user1Cluster.getId())
                            .header("Authorization", "Bearer " + user2Token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("should reject invalid token")
        void shouldRejectInvalidToken() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer invalid.token.here"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject expired token format")
        void shouldRejectMalformedToken() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject missing Bearer prefix")
        void shouldRejectMissingBearerPrefix() throws Exception {
            mockMvc.perform(get("/api/v1/clusters")
                            .header("Authorization", user1Token))
                    .andExpect(status().isUnauthorized());
        }
    }
}
