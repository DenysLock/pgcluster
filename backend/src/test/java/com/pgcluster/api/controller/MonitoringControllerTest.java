package com.pgcluster.api.controller;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.UserRepository;
import com.pgcluster.api.repository.VpsNodeRepository;
import com.pgcluster.api.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Monitoring Controller")
class MonitoringControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ClusterRepository clusterRepository;
    @Autowired private VpsNodeRepository vpsNodeRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private EntityManager entityManager;

    @Value("${security.internal-api-key}")
    private String internalApiKey;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("monitor-test@test.com")
                .passwordHash("$2a$10$dummy")
                .role("USER")
                .active(true)
                .build();
        user = userRepository.save(user);

        Cluster cluster = Cluster.builder()
                .user(user)
                .name("Monitor Cluster")
                .slug("monitor-cluster-1234")
                .plan("starter")
                .status("running")
                .postgresVersion("16")
                .nodeCount(3)
                .nodeSize("cx23")
                .region("fsn1")
                .build();
        cluster = clusterRepository.save(cluster);

        VpsNode node = VpsNode.builder()
                .cluster(cluster)
                .name("monitor-node-1")
                .publicIp("10.0.0.1")
                .status("running")
                .role("leader")
                .serverType("cx23")
                .location("fsn1")
                .build();
        vpsNodeRepository.save(node);
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("GET /internal/prometheus-targets")
    class PrometheusTargets {

        @Test
        @DisplayName("should return targets with valid API key")
        void shouldReturnTargets() throws Exception {
            mockMvc.perform(get("/internal/prometheus-targets")
                            .header("X-Internal-Api-Key", internalApiKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("should include node targets with labels")
        void shouldIncludeNodeTargets() throws Exception {
            mockMvc.perform(get("/internal/prometheus-targets")
                            .header("X-Internal-Api-Key", internalApiKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].targets", hasSize(3)))
                    .andExpect(jsonPath("$[0].targets[0]", containsString("10.0.0.1")))
                    .andExpect(jsonPath("$[0].labels.cluster_slug", is("monitor-cluster-1234")))
                    .andExpect(jsonPath("$[0].labels.node_role", is("leader")));
        }

        @Test
        @DisplayName("should reject without API key")
        void shouldRejectWithoutKey() throws Exception {
            mockMvc.perform(get("/internal/prometheus-targets"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject with wrong API key")
        void shouldRejectWithWrongKey() throws Exception {
            mockMvc.perform(get("/internal/prometheus-targets")
                            .header("X-Internal-Api-Key", "wrong-key"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /internal/cluster-metrics")
    class ClusterMetricsSummary {

        @Test
        @DisplayName("should return cluster metrics summary with valid API key")
        void shouldReturnMetricsSummary() throws Exception {
            mockMvc.perform(get("/internal/cluster-metrics")
                            .header("X-Internal-Api-Key", internalApiKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clusters_by_status", notNullValue()));
        }
    }

    @Nested
    @DisplayName("Authorization: Bearer for internal endpoints")
    class BearerAuth {

        @Test
        @DisplayName("should accept Bearer token as internal API key")
        void shouldAcceptBearerAsApiKey() throws Exception {
            mockMvc.perform(get("/internal/prometheus-targets")
                            .header("Authorization", "Bearer " + internalApiKey))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should reject JWT token for internal endpoints")
        void shouldRejectJwtForInternal() throws Exception {
            User user = userRepository.findAll().get(0);
            String jwt = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole(), true);

            mockMvc.perform(get("/internal/prometheus-targets")
                            .header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isUnauthorized());
        }
    }
}
