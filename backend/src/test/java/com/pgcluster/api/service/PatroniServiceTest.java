package com.pgcluster.api.service;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.VpsNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("PatroniService")
@ExtendWith(MockitoExtension.class)
class PatroniServiceTest {

    @Mock
    private SshService sshService;

    @Mock
    private VpsNodeRepository vpsNodeRepository;

    @InjectMocks
    private PatroniService patroniService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(patroniService, "patroniTimeoutMs", 1000);
    }

    @Nested
    @DisplayName("parseRole")
    class ParseRole {

        @Test
        @DisplayName("should return 'unknown' for null input")
        void nullInput() {
            assertThat(patroniService.parseRole(null)).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should return 'unknown' when no role field")
        void noRoleField() {
            assertThat(patroniService.parseRole("{\"state\": \"running\"}")).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should return 'leader' for master role")
        void masterRole() {
            assertThat(patroniService.parseRole("{\"role\": \"master\"}")).isEqualTo("leader");
            assertThat(patroniService.parseRole("{\"role\":\"master\"}")).isEqualTo("leader");
        }

        @Test
        @DisplayName("should return 'leader' for primary role")
        void primaryRole() {
            assertThat(patroniService.parseRole("{\"role\": \"primary\"}")).isEqualTo("leader");
            assertThat(patroniService.parseRole("{\"role\":\"primary\"}")).isEqualTo("leader");
        }

        @Test
        @DisplayName("should return 'leader' for leader role")
        void leaderRole() {
            assertThat(patroniService.parseRole("{\"role\": \"leader\"}")).isEqualTo("leader");
            assertThat(patroniService.parseRole("{\"role\":\"leader\"}")).isEqualTo("leader");
        }

        @Test
        @DisplayName("should return 'replica' for replica role")
        void replicaRole() {
            assertThat(patroniService.parseRole("{\"role\": \"replica\"}")).isEqualTo("replica");
            assertThat(patroniService.parseRole("{\"role\":\"replica\"}")).isEqualTo("replica");
        }

        @Test
        @DisplayName("should return 'unknown' for unrecognized role")
        void unknownRole() {
            assertThat(patroniService.parseRole("{\"role\": \"standby\"}")).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("isLeaderRole")
    class IsLeaderRole {

        @Test
        @DisplayName("should return true for leader output")
        void leaderOutput() {
            assertThat(patroniService.isLeaderRole("{\"role\": \"master\"}")).isTrue();
        }

        @Test
        @DisplayName("should return false for replica output")
        void replicaOutput() {
            assertThat(patroniService.isLeaderRole("{\"role\": \"replica\"}")).isFalse();
        }

        @Test
        @DisplayName("should return false for null output")
        void nullOutput() {
            assertThat(patroniService.isLeaderRole(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isReplicaRole")
    class IsReplicaRole {

        @Test
        @DisplayName("should return true for replica output")
        void replicaOutput() {
            assertThat(patroniService.isReplicaRole("{\"role\": \"replica\"}")).isTrue();
        }

        @Test
        @DisplayName("should return false for leader output")
        void leaderOutput() {
            assertThat(patroniService.isReplicaRole("{\"role\": \"master\"}")).isFalse();
        }
    }

    @Nested
    @DisplayName("getStateForRole")
    class GetStateForRole {

        @Test
        @DisplayName("should return 'running' for leader")
        void leaderState() {
            assertThat(patroniService.getStateForRole("leader")).isEqualTo("running");
        }

        @Test
        @DisplayName("should return 'streaming' for replica")
        void replicaState() {
            assertThat(patroniService.getStateForRole("replica")).isEqualTo("streaming");
        }

        @Test
        @DisplayName("should return 'unknown' for unknown role")
        void unknownState() {
            assertThat(patroniService.getStateForRole("unknown")).isEqualTo("unknown");
            assertThat(patroniService.getStateForRole("other")).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("getPatroniStatus")
    class GetPatroniStatus {

        @Test
        @DisplayName("should return null for null node")
        void nullNode() {
            assertThat(patroniService.getPatroniStatus(null)).isNull();
        }

        @Test
        @DisplayName("should return SSH result when HTTP fails and SSH succeeds")
        void sshFallback() {
            VpsNode node = createNode("10.0.0.1");
            String expectedOutput = "{\"role\": \"master\"}";

            SshService.CommandResult result = mock(SshService.CommandResult.class);
            when(result.isSuccess()).thenReturn(true);
            when(result.getStdout()).thenReturn(expectedOutput);
            when(sshService.executeCommandWithRetry(eq("10.0.0.1"), anyString(), anyInt()))
                    .thenReturn(result);

            // HTTP will fail (no server running) → falls through to SSH
            String output = patroniService.getPatroniStatus(node);

            assertThat(output).isEqualTo(expectedOutput);
        }

        @Test
        @DisplayName("should return null when both HTTP and SSH fail")
        void bothFail() {
            VpsNode node = createNode("10.0.0.1");

            SshService.CommandResult result = mock(SshService.CommandResult.class);
            when(result.isSuccess()).thenReturn(false);
            when(sshService.executeCommandWithRetry(eq("10.0.0.1"), anyString(), anyInt()))
                    .thenReturn(result);

            String output = patroniService.getPatroniStatus(node);

            assertThat(output).isNull();
        }

        @Test
        @DisplayName("should return null when SSH throws exception")
        void sshThrows() {
            VpsNode node = createNode("10.0.0.1");

            when(sshService.executeCommandWithRetry(eq("10.0.0.1"), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("SSH error"));

            String output = patroniService.getPatroniStatus(node);

            assertThat(output).isNull();
        }
    }

    @Nested
    @DisplayName("findLeaderNode(List)")
    class FindLeaderNodeList {

        @Test
        @DisplayName("should return null for null list")
        void nullList() {
            assertThat(patroniService.findLeaderNode((List<VpsNode>) null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty list")
        void emptyList() {
            assertThat(patroniService.findLeaderNode(List.of())).isNull();
        }

        @Test
        @DisplayName("should find leader node when SSH returns leader output")
        void findsLeader() {
            VpsNode node1 = createNode("10.0.0.1");
            VpsNode node2 = createNode("10.0.0.2");

            SshService.CommandResult leaderResult = mock(SshService.CommandResult.class);
            when(leaderResult.isSuccess()).thenReturn(true);
            when(leaderResult.getStdout()).thenReturn("{\"role\": \"master\"}");

            SshService.CommandResult replicaResult = mock(SshService.CommandResult.class);
            when(replicaResult.isSuccess()).thenReturn(true);
            when(replicaResult.getStdout()).thenReturn("{\"role\": \"replica\"}");

            when(sshService.executeCommandWithRetry(eq("10.0.0.1"), anyString(), anyInt()))
                    .thenReturn(leaderResult);
            when(sshService.executeCommandWithRetry(eq("10.0.0.2"), anyString(), anyInt()))
                    .thenReturn(replicaResult);

            VpsNode leader = patroniService.findLeaderNode(List.of(node1, node2));

            assertThat(leader).isNotNull();
            assertThat(leader.getPublicIp()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should return null when no leader found")
        void noLeader() {
            VpsNode node = createNode("10.0.0.1");

            SshService.CommandResult result = mock(SshService.CommandResult.class);
            when(result.isSuccess()).thenReturn(false);
            when(sshService.executeCommandWithRetry(anyString(), anyString(), anyInt()))
                    .thenReturn(result);

            VpsNode leader = patroniService.findLeaderNode(List.of(node));

            assertThat(leader).isNull();
        }
    }

    @Nested
    @DisplayName("findLeaderNode(Cluster)")
    class FindLeaderNodeCluster {

        @Test
        @DisplayName("should query repository for cluster nodes")
        void queriesRepository() {
            Cluster cluster = Cluster.builder().id(UUID.randomUUID()).build();
            VpsNode node = createNode("10.0.0.1");

            when(vpsNodeRepository.findByCluster(cluster)).thenReturn(List.of(node));

            SshService.CommandResult result = mock(SshService.CommandResult.class);
            when(result.isSuccess()).thenReturn(false);
            when(sshService.executeCommandWithRetry(anyString(), anyString(), anyInt()))
                    .thenReturn(result);

            patroniService.findLeaderNode(cluster);

            verify(vpsNodeRepository).findByCluster(cluster);
        }
    }

    @Nested
    @DisplayName("findLeaderIp")
    class FindLeaderIp {

        @Test
        @DisplayName("should throw when node list is empty")
        void emptyListThrows() {
            assertThatThrownBy(() -> patroniService.findLeaderIp(List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No nodes available");
        }

        @Test
        @DisplayName("should fall back to first node when no leader found")
        void fallbackToFirstNode() {
            VpsNode node = createNode("10.0.0.1");

            SshService.CommandResult result = mock(SshService.CommandResult.class);
            when(result.isSuccess()).thenReturn(false);
            when(sshService.executeCommandWithRetry(anyString(), anyString(), anyInt()))
                    .thenReturn(result);

            String ip = patroniService.findLeaderIp(List.of(node));

            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should return leader IP when leader found")
        void returnsLeaderIp() {
            VpsNode leader = createNode("10.0.0.1");
            VpsNode replica = createNode("10.0.0.2");

            SshService.CommandResult leaderResult = mock(SshService.CommandResult.class);
            when(leaderResult.isSuccess()).thenReturn(true);
            when(leaderResult.getStdout()).thenReturn("{\"role\": \"primary\"}");

            SshService.CommandResult replicaResult = mock(SshService.CommandResult.class);
            when(replicaResult.isSuccess()).thenReturn(true);
            when(replicaResult.getStdout()).thenReturn("{\"role\": \"replica\"}");

            when(sshService.executeCommandWithRetry(eq("10.0.0.1"), anyString(), anyInt()))
                    .thenReturn(leaderResult);
            when(sshService.executeCommandWithRetry(eq("10.0.0.2"), anyString(), anyInt()))
                    .thenReturn(replicaResult);

            String ip = patroniService.findLeaderIp(List.of(leader, replica));

            assertThat(ip).isEqualTo("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("isLeaderNode")
    class IsLeaderNode {

        @Test
        @DisplayName("should return true when node is leader")
        void nodeIsLeader() {
            VpsNode node = createNode("10.0.0.1");

            SshService.CommandResult result = mock(SshService.CommandResult.class);
            when(result.isSuccess()).thenReturn(true);
            when(result.getStdout()).thenReturn("{\"role\": \"master\"}");
            when(sshService.executeCommandWithRetry(anyString(), anyString(), anyInt()))
                    .thenReturn(result);

            assertThat(patroniService.isLeaderNode(node)).isTrue();
        }

        @Test
        @DisplayName("should return false when Patroni status is null")
        void noStatus() {
            VpsNode node = createNode("10.0.0.1");

            SshService.CommandResult result = mock(SshService.CommandResult.class);
            when(result.isSuccess()).thenReturn(false);
            when(sshService.executeCommandWithRetry(anyString(), anyString(), anyInt()))
                    .thenReturn(result);

            assertThat(patroniService.isLeaderNode(node)).isFalse();
        }
    }

    // ==================== Helpers ====================

    private VpsNode createNode(String ip) {
        return VpsNode.builder()
                .id(UUID.randomUUID())
                .name("node-" + ip)
                .publicIp(ip)
                .build();
    }
}
