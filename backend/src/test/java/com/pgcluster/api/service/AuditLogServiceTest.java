package com.pgcluster.api.service;

import com.pgcluster.api.model.dto.AuditLogFilterRequest;
import com.pgcluster.api.model.dto.AuditLogListResponse;
import com.pgcluster.api.model.dto.AuditLogResponse;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuditLogService")
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Nested
    @DisplayName("log")
    class Log {

        @Test
        @DisplayName("should save audit log with user details")
        void shouldSaveWithUserDetails() {
            User user = createTestUser();
            UUID resourceId = UUID.randomUUID();
            Map<String, Object> details = Map.of("key", "value");

            auditLogService.log(AuditLog.CLUSTER_CREATED, user, "cluster", resourceId, details);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(user.getId());
            assertThat(saved.getUserEmail()).isEqualTo(user.getEmail());
            assertThat(saved.getAction()).isEqualTo(AuditLog.CLUSTER_CREATED);
            assertThat(saved.getResourceType()).isEqualTo("cluster");
            assertThat(saved.getResourceId()).isEqualTo(resourceId);
            assertThat(saved.getDetails()).isEqualTo(details);
            assertThat(saved.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should handle null user gracefully")
        void shouldHandleNullUser() {
            auditLogService.log("SOME_ACTION", null, "resource", null, null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getUserId()).isNull();
            assertThat(saved.getUserEmail()).isNull();
        }

        @Test
        @DisplayName("should save with null IP and userAgent when no request context")
        void shouldSaveWithNullIpWhenNoRequestContext() {
            User user = createTestUser();

            auditLogService.log(AuditLog.CLUSTER_CREATED, user, "cluster", null, null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getIpAddress()).isNull();
            assertThat(saved.getUserAgent()).isNull();
        }
    }

    @Nested
    @DisplayName("logAsync with IP")
    class LogAsyncWithIp {

        @Test
        @DisplayName("should save audit log with pre-captured IP and user-agent")
        void shouldSaveWithPreCapturedIp() {
            User user = createTestUser();
            UUID resourceId = UUID.randomUUID();
            Map<String, Object> details = Map.of("action_type", "export");

            auditLogService.logAsync(AuditLog.EXPORT_INITIATED, user, "export", resourceId, details,
                    "192.168.1.100", "TestBrowser/1.0");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getIpAddress()).isEqualTo("192.168.1.100");
            assertThat(saved.getUserAgent()).isEqualTo("TestBrowser/1.0");
            assertThat(saved.getAction()).isEqualTo(AuditLog.EXPORT_INITIATED);
        }

        @Test
        @DisplayName("should not throw when repository fails")
        void shouldSwallowException() {
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            auditLogService.logAsync("ACTION", createTestUser(), "resource", null, null,
                    "127.0.0.1", "Agent");
            // Should not propagate
        }
    }

    @Nested
    @DisplayName("getCurrentRequestIp")
    class GetCurrentRequestIp {

        @Test
        @DisplayName("should return null when no request context")
        void shouldReturnNullWithoutContext() {
            String ip = auditLogService.getCurrentRequestIp();
            assertThat(ip).isNull();
        }
    }

    @Nested
    @DisplayName("getCurrentRequestUserAgent")
    class GetCurrentRequestUserAgent {

        @Test
        @DisplayName("should return null when no request context")
        void shouldReturnNullWithoutContext() {
            String userAgent = auditLogService.getCurrentRequestUserAgent();
            assertThat(userAgent).isNull();
        }
    }

    @Nested
    @DisplayName("logAuth")
    class LogAuth {

        @Test
        @DisplayName("should save auth log with success details")
        void shouldSaveSuccessDetails() {
            auditLogService.logAuth(AuditLog.AUTH_LOGIN_SUCCESS, "test@test.com", true, null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getUserEmail()).isEqualTo("test@test.com");
            assertThat(saved.getAction()).isEqualTo(AuditLog.AUTH_LOGIN_SUCCESS);
            assertThat(saved.getResourceType()).isEqualTo("auth");
            assertThat(saved.getDetails()).containsEntry("success", true);
        }

        @Test
        @DisplayName("should save auth log with failure reason")
        void shouldSaveFailureReason() {
            auditLogService.logAuth(AuditLog.AUTH_LOGIN_FAILURE, "test@test.com", false, "Invalid password");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getDetails()).containsEntry("success", false);
            assertThat(saved.getDetails()).containsEntry("reason", "Invalid password");
        }

        @Test
        @DisplayName("should use 'unknown' when failure reason is null")
        void shouldHandleNullFailureReason() {
            auditLogService.logAuth(AuditLog.AUTH_LOGIN_FAILURE, "test@test.com", false, null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getDetails()).containsEntry("reason", "unknown");
        }

        @Test
        @DisplayName("should not throw when repository throws exception")
        void shouldSwallowRepositoryException() {
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            // Should not propagate exception
            auditLogService.logAuth(AuditLog.AUTH_LOGIN_SUCCESS, "test@test.com", true, null);
        }
    }

    @Nested
    @DisplayName("getAuditLogs")
    class GetAuditLogs {

        @Test
        @DisplayName("should return paginated response")
        void shouldReturnPaginatedResponse() {
            AuditLog log1 = createTestAuditLog(AuditLog.AUTH_LOGIN_SUCCESS, "user1@test.com");
            AuditLog log2 = createTestAuditLog(AuditLog.CLUSTER_CREATED, "user2@test.com");
            Page<AuditLog> page = new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 2), 10);

            when(auditLogRepository.findWithFilters(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            AuditLogFilterRequest filter = new AuditLogFilterRequest();
            AuditLogListResponse response = auditLogService.getAuditLogs(filter, 0, 50);

            assertThat(response.getLogs()).hasSize(2);
            assertThat(response.getTotalElements()).isEqualTo(10);
            assertThat(response.getTotalPages()).isEqualTo(5);
            assertThat(response.getPage()).isEqualTo(0);
            assertThat(response.getSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("should cap page size at 100")
        void shouldCapPageSizeAt100() {
            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
            when(auditLogRepository.findWithFilters(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(emptyPage);

            AuditLogFilterRequest filter = new AuditLogFilterRequest();
            auditLogService.getAuditLogs(filter, 0, 500);

            ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(auditLogRepository).findWithFilters(any(), any(), any(), any(), any(), any(), pageCaptor.capture());

            assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("should pass all filter parameters to repository")
        void shouldPassAllFilterParams() {
            UUID userId = UUID.randomUUID();
            UUID clusterId = UUID.randomUUID();
            Instant start = Instant.parse("2026-01-01T00:00:00Z");
            Instant end = Instant.parse("2026-02-01T00:00:00Z");

            AuditLogFilterRequest filter = new AuditLogFilterRequest();
            filter.setUserId(userId);
            filter.setClusterId(clusterId);
            filter.setAction("CLUSTER_CREATED");
            filter.setResourceType("cluster");
            filter.setStartDate(start);
            filter.setEndDate(end);

            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
            when(auditLogRepository.findWithFilters(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(emptyPage);

            auditLogService.getAuditLogs(filter, 0, 50);

            verify(auditLogRepository).findWithFilters(
                    eq(userId),
                    eq(clusterId.toString()),
                    eq("CLUSTER_CREATED"),
                    eq("cluster"),
                    eq(start),
                    eq(end),
                    any(Pageable.class)
            );
        }

        @Test
        @DisplayName("should handle null clusterId in filter")
        void shouldHandleNullClusterId() {
            AuditLogFilterRequest filter = new AuditLogFilterRequest();
            filter.setClusterId(null);

            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
            when(auditLogRepository.findWithFilters(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(emptyPage);

            auditLogService.getAuditLogs(filter, 0, 50);

            verify(auditLogRepository).findWithFilters(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
            );
        }
    }

    @Nested
    @DisplayName("exportAuditLogsCsv")
    class ExportAuditLogsCsv {

        @Test
        @DisplayName("should generate valid CSV header")
        void shouldGenerateHeader() {
            when(auditLogRepository.findWithFiltersForExport(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            String csv = auditLogService.exportAuditLogsCsv(new AuditLogFilterRequest());

            assertThat(csv).startsWith("timestamp,user_email,action,resource_type,resource_id,ip_address,user_agent,details\n");
        }

        @Test
        @DisplayName("should escape fields containing commas")
        void shouldEscapeCommas() {
            AuditLog log = createTestAuditLog(AuditLog.AUTH_LOGIN_SUCCESS, "test@test.com");
            log.setUserAgent("Mozilla/5.0, Chrome");

            when(auditLogRepository.findWithFiltersForExport(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(log));

            String csv = auditLogService.exportAuditLogsCsv(new AuditLogFilterRequest());

            assertThat(csv).contains("\"Mozilla/5.0, Chrome\"");
        }

        @Test
        @DisplayName("should escape fields containing double quotes")
        void shouldEscapeDoubleQuotes() {
            AuditLog log = createTestAuditLog(AuditLog.AUTH_LOGIN_SUCCESS, "test@test.com");
            log.setUserAgent("value\"with\"quotes");

            when(auditLogRepository.findWithFiltersForExport(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(log));

            String csv = auditLogService.exportAuditLogsCsv(new AuditLogFilterRequest());

            assertThat(csv).contains("\"value\"\"with\"\"quotes\"");
        }

        @Test
        @DisplayName("should escape fields containing newlines")
        void shouldEscapeNewlines() {
            AuditLog log = createTestAuditLog(AuditLog.AUTH_LOGIN_SUCCESS, "test@test.com");
            log.setUserAgent("line1\nline2");

            when(auditLogRepository.findWithFiltersForExport(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(log));

            String csv = auditLogService.exportAuditLogsCsv(new AuditLogFilterRequest());

            assertThat(csv).contains("\"line1\nline2\"");
        }

        @Test
        @DisplayName("should handle null fields as empty strings")
        void shouldHandleNullFields() {
            AuditLog log = AuditLog.builder()
                    .timestamp(Instant.now())
                    .build();

            when(auditLogRepository.findWithFiltersForExport(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(log));

            String csv = auditLogService.exportAuditLogsCsv(new AuditLogFilterRequest());

            // Should not throw NPE and should have data row
            String[] lines = csv.split("\n");
            assertThat(lines).hasSize(2); // header + 1 data row
        }

        @Test
        @DisplayName("should export multiple rows")
        void shouldExportMultipleRows() {
            AuditLog log1 = createTestAuditLog(AuditLog.AUTH_LOGIN_SUCCESS, "user1@test.com");
            AuditLog log2 = createTestAuditLog(AuditLog.CLUSTER_CREATED, "user2@test.com");
            AuditLog log3 = createTestAuditLog(AuditLog.BACKUP_INITIATED, "user3@test.com");

            when(auditLogRepository.findWithFiltersForExport(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(log1, log2, log3));

            String csv = auditLogService.exportAuditLogsCsv(new AuditLogFilterRequest());

            String[] lines = csv.split("\n");
            assertThat(lines).hasSize(4); // header + 3 data rows
        }
    }

    @Nested
    @DisplayName("getUserActivity")
    class GetUserActivity {

        @Test
        @DisplayName("should return mapped audit log responses")
        void shouldReturnMappedResponses() {
            UUID userId = UUID.randomUUID();
            AuditLog log1 = createTestAuditLog(AuditLog.AUTH_LOGIN_SUCCESS, "test@test.com");
            AuditLog log2 = createTestAuditLog(AuditLog.CLUSTER_CREATED, "test@test.com");

            when(auditLogRepository.findTop50ByUserIdOrderByTimestampDesc(userId))
                    .thenReturn(List.of(log1, log2));

            List<AuditLogResponse> result = auditLogService.getUserActivity(userId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAction()).isEqualTo(AuditLog.AUTH_LOGIN_SUCCESS);
            assertThat(result.get(1).getAction()).isEqualTo(AuditLog.CLUSTER_CREATED);
        }

        @Test
        @DisplayName("should return empty list when no activity")
        void shouldReturnEmptyList() {
            UUID userId = UUID.randomUUID();
            when(auditLogRepository.findTop50ByUserIdOrderByTimestampDesc(userId))
                    .thenReturn(List.of());

            List<AuditLogResponse> result = auditLogService.getUserActivity(userId);

            assertThat(result).isEmpty();
        }
    }

    private User createTestUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("encoded-password")
                .role("user")
                .active(true)
                .build();
    }

    private AuditLog createTestAuditLog(String action, String email) {
        return AuditLog.builder()
                .id(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(UUID.randomUUID())
                .userEmail(email)
                .action(action)
                .resourceType("auth")
                .resourceId(UUID.randomUUID())
                .ipAddress("127.0.0.1")
                .build();
    }
}
