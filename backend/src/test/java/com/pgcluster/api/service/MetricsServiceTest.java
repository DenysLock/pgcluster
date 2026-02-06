package com.pgcluster.api.service;

import com.pgcluster.api.client.PrometheusClient;
import com.pgcluster.api.client.PrometheusClient.RangeQueryResponse;
import com.pgcluster.api.client.PrometheusClient.RangeQueryResult;
import com.pgcluster.api.model.dto.ClusterMetricsResponse;
import com.pgcluster.api.model.dto.ClusterMetricsResponse.MetricSeries;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.ClusterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("MetricsService")
@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock private PrometheusClient prometheusClient;
    @Mock private ClusterRepository clusterRepository;

    @InjectMocks
    private MetricsService metricsService;

    @Nested
    @DisplayName("getClusterMetrics")
    class GetClusterMetrics {

        @Test
        @DisplayName("should return metrics for valid running cluster")
        void shouldReturnMetrics() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetrics(cluster.getId(), user, "1h");

            assertThat(response.getClusterId()).isEqualTo(cluster.getId().toString());
            assertThat(response.getTimeRange()).isEqualTo("1h");
            assertThat(response.getStepSeconds()).isEqualTo(15);
        }

        @Test
        @DisplayName("should throw NOT_FOUND when cluster not found")
        void shouldThrowWhenClusterNotFound() {
            User user = createUser();
            UUID clusterId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(clusterId, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> metricsService.getClusterMetrics(clusterId, user, "1h"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("should throw SERVICE_UNAVAILABLE when cluster not running")
        void shouldThrowWhenNotRunning() {
            User user = createUser();
            Cluster cluster = createCluster(Cluster.STATUS_PENDING);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> metricsService.getClusterMetrics(cluster.getId(), user, "1h"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }

        @Test
        @DisplayName("should default to 1h when range is invalid")
        void shouldDefaultTo1hForInvalidRange() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetrics(cluster.getId(), user, "30d");

            assertThat(response.getTimeRange()).isEqualTo("1h");
            assertThat(response.getStepSeconds()).isEqualTo(15);
        }

        @Test
        @DisplayName("should use correct step for 7d range")
        void shouldUseCorrectStepFor7d() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetrics(cluster.getId(), user, "7d");

            assertThat(response.getTimeRange()).isEqualTo("7d");
            assertThat(response.getStepSeconds()).isEqualTo(1800);
        }

        @Test
        @DisplayName("should include disk limit bytes based on node size")
        void shouldIncludeDiskLimit() {
            User user = createUser();
            Cluster cluster = createRunningCluster();
            cluster.setNodeSize("cx32");

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetrics(cluster.getId(), user, "1h");

            assertThat(response.getDiskLimitBytes()).isEqualTo(80L * 1024 * 1024 * 1024);
        }
    }

    @Nested
    @DisplayName("getClusterMetricsAsAdmin")
    class GetClusterMetricsAsAdmin {

        @Test
        @DisplayName("should return metrics for admin without ownership check")
        void shouldReturnMetricsAsAdmin() {
            Cluster cluster = createRunningCluster();

            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetricsAsAdmin(cluster, "1h");

            assertThat(response.getClusterId()).isEqualTo(cluster.getId().toString());
        }

        @Test
        @DisplayName("should throw when cluster not running")
        void shouldThrowWhenNotRunning() {
            Cluster cluster = createCluster(Cluster.STATUS_ERROR);

            assertThatThrownBy(() -> metricsService.getClusterMetricsAsAdmin(cluster, "1h"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }

        @Test
        @DisplayName("should default invalid range to 1h")
        void shouldDefaultInvalidRange() {
            Cluster cluster = createRunningCluster();

            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetricsAsAdmin(cluster, "invalid");

            assertThat(response.getTimeRange()).isEqualTo("1h");
        }
    }

    @Nested
    @DisplayName("queryMetric with actual data")
    class QueryMetricWithData {

        @Test
        @DisplayName("should parse response data into MetricSeries with DataPoints")
        void shouldParseResponseData() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            RangeQueryResponse response = new RangeQueryResponse();
            response.setStatus("success");
            PrometheusClient.RangeQueryData data = new PrometheusClient.RangeQueryData();
            RangeQueryResult result1 = new RangeQueryResult();
            result1.setMetric(Map.of("node_name", "node-1", "node_role", "leader", "instance", "10.0.0.1:9100"));
            result1.setValues(List.of(
                    List.of(1700000000, "42.5"),
                    List.of(1700000015, "43.1")
            ));
            data.setResult(List.of(result1));
            response.setData(data);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster-abc123");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(response);

            ClusterMetricsResponse metrics = metricsService.getClusterMetrics(cluster.getId(), user, "1h");

            assertThat(metrics.getCpu()).isNotEmpty();
            assertThat(metrics.getCpu().get(0).getNodeName()).isEqualTo("node-1");
            assertThat(metrics.getCpu().get(0).getNodeRole()).isEqualTo("leader");
            assertThat(metrics.getCpu().get(0).getNodeIp()).isEqualTo("10.0.0.1");
            assertThat(metrics.getCpu().get(0).getData()).hasSize(2);
            assertThat(metrics.getCpu().get(0).getData().get(0).getValue()).isEqualTo(42.5);
        }

        @Test
        @DisplayName("should handle NaN values by replacing with 0.0")
        void shouldHandleNaNValues() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            RangeQueryResponse response = new RangeQueryResponse();
            response.setStatus("success");
            PrometheusClient.RangeQueryData data = new PrometheusClient.RangeQueryData();
            RangeQueryResult result1 = new RangeQueryResult();
            result1.setMetric(Map.of("node_name", "node-1", "node_role", "replica", "instance", "10.0.0.2:9100"));
            result1.setValues(List.of(
                    List.of(1700000000, "NaN"),
                    List.of(1700000015, "Infinity")
            ));
            data.setResult(List.of(result1));
            response.setData(data);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster-abc123");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(response);

            ClusterMetricsResponse metrics = metricsService.getClusterMetrics(cluster.getId(), user, "1h");

            assertThat(metrics.getCpu()).isNotEmpty();
            assertThat(metrics.getCpu().get(0).getData().get(0).getValue()).isEqualTo(0.0);
            assertThat(metrics.getCpu().get(0).getData().get(1).getValue()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should sort results with leader first")
        void shouldSortLeaderFirst() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            RangeQueryResponse response = new RangeQueryResponse();
            response.setStatus("success");
            PrometheusClient.RangeQueryData data = new PrometheusClient.RangeQueryData();

            RangeQueryResult replica = new RangeQueryResult();
            replica.setMetric(Map.of("node_name", "node-2", "node_role", "replica", "instance", "10.0.0.2:9100"));
            replica.setValues(List.of(List.of(1700000000, "10.0")));

            RangeQueryResult leader = new RangeQueryResult();
            leader.setMetric(Map.of("node_name", "node-1", "node_role", "leader", "instance", "10.0.0.1:9100"));
            leader.setValues(List.of(List.of(1700000000, "20.0")));

            data.setResult(List.of(replica, leader));
            response.setData(data);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster-abc123");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(response);

            ClusterMetricsResponse metrics = metricsService.getClusterMetrics(cluster.getId(), user, "1h");

            assertThat(metrics.getCpu()).hasSize(2);
            assertThat(metrics.getCpu().get(0).getNodeRole()).isEqualTo("leader");
            assertThat(metrics.getCpu().get(1).getNodeRole()).isEqualTo("replica");
        }

        @Test
        @DisplayName("should use node_name fallback to instance when node_name missing")
        void shouldFallbackToInstance() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            RangeQueryResponse response = new RangeQueryResponse();
            response.setStatus("success");
            PrometheusClient.RangeQueryData data = new PrometheusClient.RangeQueryData();
            RangeQueryResult result1 = new RangeQueryResult();
            result1.setMetric(Map.of("instance", "10.0.0.1:9100"));
            result1.setValues(List.of(List.of(1700000000, "50.0")));
            data.setResult(List.of(result1));
            response.setData(data);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster-abc123");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(response);

            ClusterMetricsResponse metrics = metricsService.getClusterMetrics(cluster.getId(), user, "1h");

            assertThat(metrics.getCpu().get(0).getNodeName()).isEqualTo("10.0.0.1:9100");
            assertThat(metrics.getCpu().get(0).getNodeRole()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should skip results with null metric map")
        void shouldSkipNullMetric() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            RangeQueryResponse response = new RangeQueryResponse();
            response.setStatus("success");
            PrometheusClient.RangeQueryData data = new PrometheusClient.RangeQueryData();
            RangeQueryResult result1 = new RangeQueryResult();
            result1.setMetric(null);
            result1.setValues(List.of(List.of(1700000000, "50.0")));
            data.setResult(List.of(result1));
            response.setData(data);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster-abc123");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(response);

            ClusterMetricsResponse metrics = metricsService.getClusterMetrics(cluster.getId(), user, "1h");

            assertThat(metrics.getCpu()).isEmpty();
        }

        @Test
        @DisplayName("should use correct step for 6h range")
        void shouldUseCorrectStepFor6h() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetrics(cluster.getId(), user, "6h");

            assertThat(response.getTimeRange()).isEqualTo("6h");
            assertThat(response.getStepSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("should use correct step for 24h range")
        void shouldUseCorrectStepFor24h() {
            User user = createUser();
            Cluster cluster = createRunningCluster();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(prometheusClient.escapeLabel(anyString())).thenReturn("test-cluster");
            when(prometheusClient.queryRange(anyString(), anyLong(), anyLong(), anyString())).thenReturn(null);

            ClusterMetricsResponse response = metricsService.getClusterMetrics(cluster.getId(), user, "24h");

            assertThat(response.getTimeRange()).isEqualTo("24h");
            assertThat(response.getStepSeconds()).isEqualTo(300);
        }
    }

    // ==================== Helpers ====================

    private User createUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .role("user")
                .active(true)
                .build();
    }

    private Cluster createRunningCluster() {
        return createCluster(Cluster.STATUS_RUNNING);
    }

    private Cluster createCluster(String status) {
        return Cluster.builder()
                .id(UUID.randomUUID())
                .name("test-cluster")
                .slug("test-cluster-abc123")
                .status(status)
                .nodeSize("cx23")
                .build();
    }
}
