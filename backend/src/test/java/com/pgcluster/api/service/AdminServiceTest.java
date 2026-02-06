package com.pgcluster.api.service;

import com.pgcluster.api.event.ClusterDeleteRequestedEvent;
import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.AdminStatsResponse;
import com.pgcluster.api.model.dto.AdminUserResponse;
import com.pgcluster.api.model.dto.CreateUserRequest;
import com.pgcluster.api.model.dto.ResetPasswordRequest;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.Export;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AdminService")
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private ClusterRepository clusterRepository;
    @Mock private UserRepository userRepository;
    @Mock private BackupRepository backupRepository;
    @Mock private ExportRepository exportRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MetricsService metricsService;
    @Mock private BackupService backupService;
    @Mock private ExportService exportService;
    @Mock private VpsNodeRepository vpsNodeRepository;
    @Mock private SshService sshService;
    @Mock private PatroniService patroniService;

    @InjectMocks
    private AdminService adminService;

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("should aggregate platform statistics excluding deleted clusters")
        void shouldAggregateStats() {
            when(clusterRepository.countByStatusNot(Cluster.STATUS_DELETED)).thenReturn(5L);
            when(clusterRepository.countByStatus(Cluster.STATUS_RUNNING)).thenReturn(3L);
            when(userRepository.count()).thenReturn(10L);

            AdminStatsResponse stats = adminService.getStats();

            assertThat(stats.getTotalClusters()).isEqualTo(5);
            assertThat(stats.getRunningClusters()).isEqualTo(3);
            assertThat(stats.getTotalUsers()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user with encoded password and default role")
        void shouldCreateUser() {
            User admin = createAdmin();
            CreateUserRequest request = createCreateUserRequest();
            request.setRole(null); // default

            when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            AdminUserResponse response = adminService.createUser(request, admin);

            assertThat(response.getEmail()).isEqualTo("newuser@example.com");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.getPasswordHash()).isEqualTo("encoded-hash");
            assertThat(saved.getRole()).isEqualTo("user");
            assertThat(saved.isActive()).isTrue();

            verify(auditLogService).log(eq(AuditLog.USER_CREATED), eq(admin), eq("user"), any(), any());
        }

        @Test
        @DisplayName("should lowercase email")
        void shouldLowercaseEmail() {
            User admin = createAdmin();
            CreateUserRequest request = createCreateUserRequest();
            request.setEmail("Admin@Example.COM");

            when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            adminService.createUser(request, admin);

            verify(userRepository).existsByEmail("admin@example.com");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("admin@example.com");
        }

        @Test
        @DisplayName("should throw CONFLICT when email already exists")
        void shouldThrowOnDuplicateEmail() {
            User admin = createAdmin();
            CreateUserRequest request = createCreateUserRequest();

            when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);

            assertThatThrownBy(() -> adminService.createUser(request, admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(ex.getMessage()).isEqualTo("Email already registered");
                    });

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BAD_REQUEST for invalid role")
        void shouldThrowOnInvalidRole() {
            User admin = createAdmin();
            CreateUserRequest request = createCreateUserRequest();
            request.setRole("superadmin");

            when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);

            assertThatThrownBy(() -> adminService.createUser(request, admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        @DisplayName("should accept admin role")
        void shouldAcceptAdminRole() {
            User admin = createAdmin();
            CreateUserRequest request = createCreateUserRequest();
            request.setRole("admin");

            when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            adminService.createUser(request, admin);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("disableUser")
    class DisableUser {

        @Test
        @DisplayName("should disable user and audit log it")
        void shouldDisableUser() {
            User admin = createAdmin();
            User target = createTargetUser();

            when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminUserResponse response = adminService.disableUser(target.getId(), admin);

            assertThat(response.isActive()).isFalse();
            verify(auditLogService).log(eq(AuditLog.USER_DISABLED), eq(admin), eq("user"), eq(target.getId()), any());
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when disabling self")
        void shouldThrowWhenDisablingSelf() {
            User admin = createAdmin();

            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> adminService.disableUser(admin.getId(), admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).isEqualTo("Cannot disable your own account");
                    });
        }

        @Test
        @DisplayName("should throw NOT_FOUND when user does not exist")
        void shouldThrowWhenUserNotFound() {
            User admin = createAdmin();
            UUID unknownId = UUID.randomUUID();

            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.disableUser(unknownId, admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("enableUser")
    class EnableUser {

        @Test
        @DisplayName("should enable user and audit log it")
        void shouldEnableUser() {
            User admin = createAdmin();
            User target = createTargetUser();
            target.setActive(false);

            when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminUserResponse response = adminService.enableUser(target.getId(), admin);

            assertThat(response.isActive()).isTrue();
            verify(auditLogService).log(eq(AuditLog.USER_ENABLED), eq(admin), eq("user"), eq(target.getId()), any());
        }
    }

    @Nested
    @DisplayName("resetUserPassword")
    class ResetUserPassword {

        @Test
        @DisplayName("should encode new password and save")
        void shouldResetPassword() {
            User admin = createAdmin();
            User target = createTargetUser();
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setNewPassword("newSecurePassword123");

            when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
            when(passwordEncoder.encode("newSecurePassword123")).thenReturn("new-encoded-hash");

            adminService.resetUserPassword(target.getId(), request, admin);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("new-encoded-hash");

            verify(auditLogService).log(eq(AuditLog.USER_PASSWORD_RESET), eq(admin), eq("user"), eq(target.getId()), any());
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when resetting own password")
        void shouldThrowWhenResettingOwnPassword() {
            User admin = createAdmin();
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setNewPassword("newPassword");

            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> adminService.resetUserPassword(admin.getId(), request, admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("Cannot reset your own password");
                    });
        }

        @Test
        @DisplayName("should throw NOT_FOUND when user does not exist")
        void shouldThrowWhenUserNotFound() {
            User admin = createAdmin();
            UUID unknownId = UUID.randomUUID();
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setNewPassword("newPassword");

            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.resetUserPassword(unknownId, request, admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("deleteClusterAsAdmin")
    class DeleteClusterAsAdmin {

        @Test
        @DisplayName("should set status to DELETING, publish event, and audit log")
        void shouldDeleteCluster() {
            User admin = createAdmin();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));
            when(clusterRepository.save(any(Cluster.class))).thenAnswer(inv -> inv.getArgument(0));

            adminService.deleteClusterAsAdmin(cluster.getId(), admin);

            ArgumentCaptor<Cluster> captor = ArgumentCaptor.forClass(Cluster.class);
            verify(clusterRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(Cluster.STATUS_DELETING);

            verify(eventPublisher).publishEvent(any(ClusterDeleteRequestedEvent.class));
            verify(auditLogService).log(eq(AuditLog.CLUSTER_DELETED), eq(admin), eq("cluster"), eq(cluster.getId()), any());
        }

        @Test
        @DisplayName("should throw CONFLICT when cluster is already deleting")
        void shouldThrowWhenAlreadyDeleting() {
            User admin = createAdmin();
            Cluster cluster = createCluster(Cluster.STATUS_DELETING);

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> adminService.deleteClusterAsAdmin(cluster.getId(), admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("should throw CONFLICT when cluster is already deleted")
        void shouldThrowWhenAlreadyDeleted() {
            User admin = createAdmin();
            Cluster cluster = createCluster(Cluster.STATUS_DELETED);

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> adminService.deleteClusterAsAdmin(cluster.getId(), admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("should throw NOT_FOUND when cluster does not exist")
        void shouldThrowWhenClusterNotFound() {
            User admin = createAdmin();
            UUID unknownId = UUID.randomUUID();

            when(clusterRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.deleteClusterAsAdmin(unknownId, admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("listAllClusters")
    class ListAllClusters {

        @Test
        @DisplayName("should return clusters excluding deleted by default")
        void shouldExcludeDeleted() {
            when(clusterRepository.findAllWithUser()).thenReturn(List.of());

            var response = adminService.listAllClusters(false);

            assertThat(response.getCount()).isEqualTo(0);
            verify(clusterRepository).findAllWithUser();
            verify(clusterRepository, never()).findAllWithUserIncludingDeleted();
        }

        @Test
        @DisplayName("should include deleted clusters when requested")
        void shouldIncludeDeleted() {
            when(clusterRepository.findAllWithUserIncludingDeleted()).thenReturn(List.of());

            var response = adminService.listAllClusters(true);

            verify(clusterRepository).findAllWithUserIncludingDeleted();
        }
    }

    @Nested
    @DisplayName("getCluster")
    class GetCluster {

        @Test
        @DisplayName("should return cluster details")
        void shouldReturnCluster() {
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            when(clusterRepository.findByIdWithUserAndNodes(cluster.getId())).thenReturn(Optional.of(cluster));

            var response = adminService.getCluster(cluster.getId());

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("should throw NOT_FOUND when cluster not found")
        void shouldThrowWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(clusterRepository.findByIdWithUserAndNodes(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getCluster(unknownId))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("listAllUsers")
    class ListAllUsers {

        @Test
        @DisplayName("should return all users")
        void shouldReturnAllUsers() {
            User user = createTargetUser();
            when(userRepository.findAll()).thenReturn(List.of(user));

            var response = adminService.listAllUsers();

            assertThat(response.getCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getUserDetail")
    class GetUserDetail {

        @Test
        @DisplayName("should return user with clusters")
        void shouldReturnUserDetail() {
            User user = createTargetUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(clusterRepository.findByUserAndStatusNotOrderByCreatedAtDesc(user, Cluster.STATUS_DELETED))
                    .thenReturn(List.of());

            var response = adminService.getUserDetail(user.getId());

            assertThat(response.getEmail()).isEqualTo("target@example.com");
            assertThat(response.getClusterCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should throw NOT_FOUND when user not found")
        void shouldThrowWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getUserDetail(unknownId))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("getClusterCredentialsAsAdmin")
    class GetClusterCredentialsAsAdmin {

        @Test
        @DisplayName("should return credentials when cluster has hostname and password")
        void shouldReturnCredentials() {
            User admin = createAdmin();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            cluster.setHostname("test-cluster.pgcluster.local");
            cluster.setPort(5432);
            cluster.setPostgresPassword("secret-password");

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));

            var response = adminService.getClusterCredentialsAsAdmin(cluster.getId(), admin);

            assertThat(response.getHostname()).isEqualTo("test-cluster.pgcluster.local");
            assertThat(response.getPassword()).isEqualTo("secret-password");
            verify(auditLogService).log(eq(AuditLog.CREDENTIALS_ACCESSED), eq(admin), eq("cluster"), eq(cluster.getId()), any());
        }

        @Test
        @DisplayName("should throw SERVICE_UNAVAILABLE when credentials not yet available")
        void shouldThrowWhenCredentialsNotAvailable() {
            User admin = createAdmin();
            Cluster cluster = createCluster(Cluster.STATUS_CREATING);
            cluster.setHostname(null);
            cluster.setPostgresPassword(null);

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> adminService.getClusterCredentialsAsAdmin(cluster.getId(), admin))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    @Nested
    @DisplayName("getClusterBackupsAsAdmin")
    class GetClusterBackupsAsAdmin {

        @Test
        @DisplayName("should return backups excluding deleted by default")
        void shouldExcludeDeleted() {
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            Backup activeBackup = Backup.builder().id(UUID.randomUUID()).status(Backup.STATUS_COMPLETED).build();
            Backup deletedBackup = Backup.builder().id(UUID.randomUUID()).status(Backup.STATUS_DELETED).build();

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterOrderByCreatedAtDesc(cluster))
                    .thenReturn(List.of(activeBackup, deletedBackup));

            List<Backup> result = adminService.getClusterBackupsAsAdmin(cluster.getId(), false);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(Backup.STATUS_COMPLETED);
        }

        @Test
        @DisplayName("should include deleted when requested")
        void shouldIncludeDeleted() {
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            Backup activeBackup = Backup.builder().id(UUID.randomUUID()).status(Backup.STATUS_COMPLETED).build();
            Backup deletedBackup = Backup.builder().id(UUID.randomUUID()).status(Backup.STATUS_DELETED).build();

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterOrderByCreatedAtDesc(cluster))
                    .thenReturn(List.of(activeBackup, deletedBackup));

            List<Backup> result = adminService.getClusterBackupsAsAdmin(cluster.getId(), true);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getClusterExportsAsAdmin")
    class GetClusterExportsAsAdmin {

        @Test
        @DisplayName("should return exports for cluster")
        void shouldReturnExports() {
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            Export export = Export.builder().id(UUID.randomUUID()).status(Export.STATUS_COMPLETED).build();

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));
            when(exportRepository.findByClusterOrderByCreatedAtDesc(cluster)).thenReturn(List.of(export));

            List<Export> result = adminService.getClusterExportsAsAdmin(cluster.getId());

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw NOT_FOUND when cluster not found")
        void shouldThrowWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(clusterRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getClusterExportsAsAdmin(unknownId))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("deleteBackupAsAdmin")
    class DeleteBackupAsAdmin {

        @Test
        @DisplayName("should delegate to backup service and audit log")
        void shouldDeleteAndAudit() {
            User admin = createAdmin();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            Backup backup = Backup.builder().id(UUID.randomUUID()).build();

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));

            adminService.deleteBackupAsAdmin(cluster.getId(), backup.getId(), true, admin);

            verify(backupService).deleteBackupAsAdmin(cluster, backup, true);
            verify(auditLogService).log(eq(AuditLog.BACKUP_DELETED), eq(admin), eq("backup"), eq(backup.getId()), any());
        }
    }

    @Nested
    @DisplayName("deleteExportAsAdmin")
    class DeleteExportAsAdmin {

        @Test
        @DisplayName("should delegate to export service and audit log")
        void shouldDeleteAndAudit() {
            User admin = createAdmin();
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            Export export = Export.builder().id(UUID.randomUUID()).build();

            when(clusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));

            adminService.deleteExportAsAdmin(cluster.getId(), export.getId(), admin);

            verify(exportService).deleteExportAsAdmin(cluster, export);
            verify(auditLogService).log(eq(AuditLog.EXPORT_DELETED), eq(admin), eq("export"), eq(export.getId()), any());
        }
    }

    @Nested
    @DisplayName("getClusterMetricsAsAdmin")
    class GetClusterMetricsAsAdmin {

        @Test
        @DisplayName("should delegate to metrics service")
        void shouldDelegateToMetrics() {
            Cluster cluster = createCluster(Cluster.STATUS_RUNNING);
            when(clusterRepository.findByIdWithUserAndNodes(cluster.getId())).thenReturn(Optional.of(cluster));

            adminService.getClusterMetricsAsAdmin(cluster.getId(), "1h");

            verify(metricsService).getClusterMetricsAsAdmin(cluster, "1h");
        }

        @Test
        @DisplayName("should throw NOT_FOUND when cluster not found")
        void shouldThrowWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(clusterRepository.findByIdWithUserAndNodes(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getClusterMetricsAsAdmin(unknownId, "1h"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ==================== Helpers ====================

    private User createAdmin() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("admin@pgcluster.com")
                .passwordHash("admin-hash")
                .role("admin")
                .active(true)
                .build();
    }

    private User createTargetUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("target@example.com")
                .passwordHash("target-hash")
                .firstName("Target")
                .lastName("User")
                .role("user")
                .active(true)
                .build();
    }

    private Cluster createCluster(String status) {
        User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        return Cluster.builder()
                .id(UUID.randomUUID())
                .name("test-cluster")
                .slug("test-cluster-abc123")
                .status(status)
                .user(owner)
                .build();
    }

    private CreateUserRequest createCreateUserRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("password123");
        request.setFirstName("New");
        request.setLastName("User");
        request.setRole("user");
        return request;
    }
}
