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
@DisplayName("Backup Controller")
class BackupControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ClusterRepository clusterRepository;
    @Autowired private BackupRepository backupRepository;
    @Autowired private ExportRepository exportRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String userToken;
    private Cluster cluster;
    private Backup backup;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("backup-test@test.com")
                .passwordHash("$2a$10$dummy")
                .role("USER")
                .active(true)
                .build();
        user = userRepository.save(user);
        userToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole(), true);

        cluster = Cluster.builder()
                .user(user)
                .name("Backup Test Cluster")
                .slug("backup-test-1234")
                .plan("starter")
                .status("running")
                .postgresVersion("16")
                .nodeCount(3)
                .nodeSize("cx23")
                .region("fsn1")
                .hostname("backup-test.pgcluster.local")
                .port(5432)
                .postgresPassword("test-password")
                .build();
        cluster = clusterRepository.save(cluster);

        backup = Backup.builder()
                .cluster(cluster)
                .type(Backup.TYPE_MANUAL)
                .status(Backup.STATUS_COMPLETED)
                .backupType(Backup.BACKUP_TYPE_FULL)
                .requestedBackupType(Backup.BACKUP_TYPE_FULL)
                .pgbackrestLabel("20260201-120000F")
                .sizeBytes(1024L * 1024L)
                .createdAt(Instant.now())
                .build();
        backup = backupRepository.save(backup);
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{clusterId}/backups")
    class ListBackups {

        @Test
        @DisplayName("should list backups for authenticated user's cluster")
        void shouldListBackups() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backups", hasSize(1)));
        }

        @Test
        @DisplayName("should require authentication")
        void shouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 404 for other user's cluster")
        void shouldReturn404ForOtherCluster() throws Exception {
            User otherUser = User.builder()
                    .email("other-backup@test.com")
                    .passwordHash("$2a$10$dummy")
                    .role("USER")
                    .active(true)
                    .build();
            otherUser = userRepository.save(otherUser);
            String otherToken = jwtTokenProvider.generateToken(otherUser.getId(), otherUser.getEmail(), otherUser.getRole(), true);

            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{clusterId}/backups/{backupId}")
    class GetBackup {

        @Test
        @DisplayName("should return backup details")
        void shouldReturnBackup() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/" + backup.getId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(backup.getId().toString())))
                    .andExpect(jsonPath("$.status", is("completed")));
        }

        @Test
        @DisplayName("should return error for non-existent backup")
        void shouldReturnErrorForNonExistent() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{clusterId}/backups/{backupId}/deletion-info")
    class GetDeletionInfo {

        @Test
        @DisplayName("should return deletion info for backup")
        void shouldReturnDeletionInfo() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/" + backup.getId() + "/deletion-info")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(1)));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clusters/{clusterId}/backups")
    class CreateBackup {

        @Test
        @DisplayName("should fail when S3 not configured (test environment)")
        void shouldFailWhenS3NotConfigured() throws Exception {
            mockMvc.perform(post("/api/v1/clusters/" + cluster.getId() + "/backups")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{clusterId}/backups/restore-jobs")
    class ListRestoreJobs {

        @Test
        @DisplayName("should return empty list when no restore jobs")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/restore-jobs")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{clusterId}/backups/exports")
    class ListExports {

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

            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/exports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clusters/{clusterId}/backups/exports")
    class CreateExport {

        @Test
        @DisplayName("should fail when S3 not configured (test environment)")
        void shouldFailWhenS3NotConfigured() throws Exception {
            mockMvc.perform(post("/api/v1/clusters/" + cluster.getId() + "/backups/exports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{clusterId}/backups/exports/{exportId}")
    class GetExport {

        @Test
        @DisplayName("should return export details")
        void shouldReturnExport() throws Exception {
            Export export = Export.builder()
                    .cluster(cluster)
                    .status(Export.STATUS_COMPLETED)
                    .format(Export.FORMAT_PG_DUMP)
                    .sizeBytes(1024L)
                    .build();
            export = exportRepository.save(export);

            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/exports/" + export.getId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(export.getId().toString())));
        }

        @Test
        @DisplayName("should return error for non-existent export")
        void shouldReturnErrorForNonExistent() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/exports/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/clusters/{clusterId}/backups/exports/{exportId}")
    class DeleteExport {

        @Test
        @DisplayName("should delete completed export")
        void shouldDeleteExport() throws Exception {
            Export export = Export.builder()
                    .cluster(cluster)
                    .status(Export.STATUS_COMPLETED)
                    .format(Export.FORMAT_PG_DUMP)
                    .sizeBytes(1024L)
                    .build();
            export = exportRepository.save(export);

            mockMvc.perform(delete("/api/v1/clusters/" + cluster.getId() + "/backups/exports/" + export.getId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", is("Export deleted successfully")));
        }

        @Test
        @DisplayName("should reject deleting in-progress export")
        void shouldRejectDeletingInProgress() throws Exception {
            Export export = Export.builder()
                    .cluster(cluster)
                    .status(Export.STATUS_IN_PROGRESS)
                    .format(Export.FORMAT_PG_DUMP)
                    .build();
            export = exportRepository.save(export);

            mockMvc.perform(delete("/api/v1/clusters/" + cluster.getId() + "/backups/exports/" + export.getId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clusters/{clusterId}/backups/metrics")
    class GetBackupMetrics {

        @Test
        @DisplayName("should return backup metrics")
        void shouldReturnMetrics() throws Exception {
            mockMvc.perform(get("/api/v1/clusters/" + cluster.getId() + "/backups/metrics")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backupCount", greaterThanOrEqualTo(0)))
                    .andExpect(jsonPath("$.totalSizeBytes", notNullValue()));
        }
    }
}
