package com.pgcluster.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.Export;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.BackupRepository;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.ExportRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Admin Controller")
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ClusterRepository clusterRepository;
    @Autowired private BackupRepository backupRepository;
    @Autowired private ExportRepository exportRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;
    private Cluster cluster;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .email("admin-ctrl@test.com")
                .passwordHash(passwordEncoder.encode("adminPass123"))
                .role("ADMIN")
                .active(true)
                .firstName("Admin")
                .lastName("Test")
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtTokenProvider.generateToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole(), true);

        regularUser = User.builder()
                .email("regular-ctrl@test.com")
                .passwordHash(passwordEncoder.encode("userPass123"))
                .role("USER")
                .active(true)
                .firstName("Regular")
                .lastName("User")
                .build();
        regularUser = userRepository.save(regularUser);
        userToken = jwtTokenProvider.generateToken(regularUser.getId(), regularUser.getEmail(), regularUser.getRole(), true);

        cluster = Cluster.builder()
                .user(regularUser)
                .name("Admin Test Cluster")
                .slug("admin-test-1234")
                .plan("starter")
                .status("running")
                .postgresVersion("16")
                .nodeCount(3)
                .nodeSize("cx23")
                .region("fsn1")
                .hostname("admin-test.pgcluster.local")
                .port(5432)
                .postgresPassword("test-password")
                .build();
        cluster = clusterRepository.save(cluster);
    }

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @DisplayName("should reject non-admin user")
        void shouldRejectNonAdmin() throws Exception {
            mockMvc.perform(get("/api/v1/admin/stats")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("should reject unauthenticated request")
        void shouldRejectUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/admin/stats"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should accept admin user")
        void shouldAcceptAdmin() throws Exception {
            mockMvc.perform(get("/api/v1/admin/stats")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/stats")
    class GetStats {

        @Test
        @DisplayName("should return platform statistics")
        void shouldReturnStats() throws Exception {
            mockMvc.perform(get("/api/v1/admin/stats")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total_clusters", greaterThanOrEqualTo(0)))
                    .andExpect(jsonPath("$.running_clusters", greaterThanOrEqualTo(0)))
                    .andExpect(jsonPath("$.total_users", greaterThanOrEqualTo(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/clusters")
    class ListClusters {

        @Test
        @DisplayName("should list all clusters")
        void shouldListAllClusters() throws Exception {
            mockMvc.perform(get("/api/v1/admin/clusters")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clusters", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.count", greaterThanOrEqualTo(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/clusters/{id}")
    class GetCluster {

        @Test
        @DisplayName("should return cluster details")
        void shouldReturnCluster() throws Exception {
            mockMvc.perform(get("/api/v1/admin/clusters/" + cluster.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug", is("admin-test-1234")));
        }

        @Test
        @DisplayName("should return 404 for non-existent cluster")
        void shouldReturn404ForNonExistent() throws Exception {
            mockMvc.perform(get("/api/v1/admin/clusters/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/clusters/{id}")
    class DeleteCluster {

        @Test
        @DisplayName("should accept delete request for running cluster")
        void shouldAcceptDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/clusters/" + cluster.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 for non-existent cluster")
        void shouldReturn404ForNonExistent() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/clusters/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users")
    class ListUsers {

        @Test
        @DisplayName("should list all users")
        void shouldListAllUsers() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$.count", greaterThanOrEqualTo(2)));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users")
    class CreateUser {

        @Test
        @DisplayName("should create a new user")
        void shouldCreateUser() throws Exception {
            String json = """
                {
                    "email": "newuser-ctrl@test.com",
                    "password": "securePassword123",
                    "first_name": "New",
                    "last_name": "User",
                    "role": "user"
                }
                """;

            mockMvc.perform(post("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email", is("newuser-ctrl@test.com")));
        }

        @Test
        @DisplayName("should return 400 for invalid request")
        void shouldReturn400ForInvalid() throws Exception {
            String json = """
                {"email": "bad-email", "password": "short"}
                """;

            mockMvc.perform(post("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject duplicate email")
        void shouldRejectDuplicateEmail() throws Exception {
            String json = """
                {
                    "email": "regular-ctrl@test.com",
                    "password": "password123",
                    "first_name": "Dup",
                    "last_name": "User"
                }
                """;

            mockMvc.perform(post("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users/{id}")
    class GetUserDetail {

        @Test
        @DisplayName("should return user details with clusters")
        void shouldReturnUserDetail() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users/" + regularUser.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is("regular-ctrl@test.com")))
                    .andExpect(jsonPath("$.cluster_count", greaterThanOrEqualTo(0)));
        }

        @Test
        @DisplayName("should return 404 for non-existent user")
        void shouldReturn404ForNonExistent() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users/{id}/disable")
    class DisableUser {

        @Test
        @DisplayName("should disable user")
        void shouldDisableUser() throws Exception {
            mockMvc.perform(post("/api/v1/admin/users/" + regularUser.getId() + "/disable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(false)));
        }

        @Test
        @DisplayName("should reject self-disable")
        void shouldRejectSelfDisable() throws Exception {
            mockMvc.perform(post("/api/v1/admin/users/" + adminUser.getId() + "/disable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users/{id}/enable")
    class EnableUser {

        @Test
        @DisplayName("should enable user")
        void shouldEnableUser() throws Exception {
            regularUser.setActive(false);
            userRepository.save(regularUser);

            mockMvc.perform(post("/api/v1/admin/users/" + regularUser.getId() + "/enable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(true)));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users/{id}/reset-password")
    class ResetPassword {

        @Test
        @DisplayName("should reset user password")
        void shouldResetPassword() throws Exception {
            String json = """
                {"new_password": "newSecurePassword123"}
                """;

            mockMvc.perform(post("/api/v1/admin/users/" + regularUser.getId() + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should reject resetting own password")
        void shouldRejectSelfReset() throws Exception {
            String json = """
                {"new_password": "newPassword123"}
                """;

            mockMvc.perform(post("/api/v1/admin/users/" + adminUser.getId() + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // Audit log endpoints (/api/v1/admin/audit-logs, /api/v1/admin/audit-logs/export)
    // use native PostgreSQL queries with jsonb operators (->>) that are not supported by H2.
    // These are tested via AdminServiceTest (unit) and manually against PostgreSQL.

    @Nested
    @DisplayName("GET /api/v1/admin/clusters/{id}/credentials")
    class GetClusterCredentials {

        @Test
        @DisplayName("should return credentials for running cluster")
        void shouldReturnCredentials() throws Exception {
            mockMvc.perform(get("/api/v1/admin/clusters/" + cluster.getId() + "/credentials")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.password", is("test-password")))
                    .andExpect(jsonPath("$.hostname", is("admin-test.pgcluster.local")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/clusters/{id}/backups")
    class GetClusterBackups {

        @Test
        @DisplayName("should return backups for cluster")
        void shouldReturnBackups() throws Exception {
            Backup backup = Backup.builder()
                    .cluster(cluster)
                    .type(Backup.TYPE_MANUAL)
                    .status(Backup.STATUS_COMPLETED)
                    .backupType(Backup.BACKUP_TYPE_FULL)
                    .requestedBackupType(Backup.BACKUP_TYPE_FULL)
                    .sizeBytes(1024L)
                    .createdAt(Instant.now())
                    .build();
            backupRepository.save(backup);

            mockMvc.perform(get("/api/v1/admin/clusters/" + cluster.getId() + "/backups")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backups", hasSize(1)));
        }

        @Test
        @DisplayName("should return empty list when no backups")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/admin/clusters/" + cluster.getId() + "/backups")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backups", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/clusters/{id}/exports")
    class GetClusterExports {

        @Test
        @DisplayName("should return exports for cluster")
        void shouldReturnExports() throws Exception {
            Export export = Export.builder()
                    .cluster(cluster)
                    .status(Export.STATUS_COMPLETED)
                    .format(Export.FORMAT_PG_DUMP)
                    .sizeBytes(512L)
                    .build();
            exportRepository.save(export);

            mockMvc.perform(get("/api/v1/admin/clusters/" + cluster.getId() + "/exports")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/clusters/{id}/exports/{exportId}")
    class DeleteClusterExport {

        @Test
        @DisplayName("should delete export")
        void shouldDeleteExport() throws Exception {
            Export export = Export.builder()
                    .cluster(cluster)
                    .status(Export.STATUS_COMPLETED)
                    .format(Export.FORMAT_PG_DUMP)
                    .sizeBytes(512L)
                    .build();
            export = exportRepository.save(export);

            mockMvc.perform(delete("/api/v1/admin/clusters/" + cluster.getId() + "/exports/" + export.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", is("Export deleted successfully")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users/{id}/activity")
    class GetUserActivity {

        @Test
        @DisplayName("should return user activity")
        void shouldReturnActivity() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users/" + regularUser.getId() + "/activity")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
