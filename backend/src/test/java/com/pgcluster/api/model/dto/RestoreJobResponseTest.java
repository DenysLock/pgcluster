package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.RestoreJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RestoreJobResponse")
class RestoreJobResponseTest {

    @Nested
    @DisplayName("fromEntity")
    class FromEntity {

        @Test
        @DisplayName("should map all fields including target cluster")
        void shouldMapAllFields() {
            Cluster source = Cluster.builder().id(UUID.randomUUID()).name("source").slug("source-slug").build();
            Cluster target = Cluster.builder().id(UUID.randomUUID()).name("target").slug("target-slug").build();
            Backup backup = Backup.builder().id(UUID.randomUUID()).build();

            RestoreJob job = RestoreJob.builder()
                    .id(UUID.randomUUID())
                    .sourceCluster(source)
                    .targetCluster(target)
                    .backup(backup)
                    .restoreType("in_place")
                    .targetTime(Instant.now())
                    .status("completed")
                    .currentStep("done")
                    .progress(100)
                    .errorMessage(null)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .completedAt(Instant.now())
                    .build();

            RestoreJobResponse response = RestoreJobResponse.fromEntity(job);

            assertThat(response.getId()).isEqualTo(job.getId());
            assertThat(response.getSourceClusterId()).isEqualTo(source.getId());
            assertThat(response.getTargetClusterId()).isEqualTo(target.getId());
            assertThat(response.getBackupId()).isEqualTo(backup.getId());
            assertThat(response.getRestoreType()).isEqualTo("in_place");
            assertThat(response.getStatus()).isEqualTo("completed");
            assertThat(response.getProgress()).isEqualTo(100);
            assertThat(response.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should handle null target cluster")
        void shouldHandleNullTargetCluster() {
            Cluster source = Cluster.builder().id(UUID.randomUUID()).name("source").slug("source-slug").build();
            Backup backup = Backup.builder().id(UUID.randomUUID()).build();

            RestoreJob job = RestoreJob.builder()
                    .id(UUID.randomUUID())
                    .sourceCluster(source)
                    .targetCluster(null)
                    .backup(backup)
                    .restoreType("in_place")
                    .status("in_progress")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            RestoreJobResponse response = RestoreJobResponse.fromEntity(job);

            assertThat(response.getTargetClusterId()).isNull();
            assertThat(response.getSourceClusterId()).isEqualTo(source.getId());
        }
    }
}
