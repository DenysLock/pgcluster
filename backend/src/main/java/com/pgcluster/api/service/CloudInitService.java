package com.pgcluster.api.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CloudInitService {

    @Value("${cluster.base-domain}")
    private String baseDomain;

    /**
     * Generate cloud-init script for a Patroni cluster node
     */
    public String generateCloudInit(CloudInitConfig config) {
        StringBuilder script = new StringBuilder();

        script.append("#!/bin/bash\n");
        script.append("set -e\n\n");

        // Logging setup
        script.append("exec > >(tee /var/log/cloud-init-output.log) 2>&1\n");
        script.append("echo \"Starting cloud-init at $(date)\"\n\n");

        // System updates
        script.append("# System updates\n");
        script.append("apt-get update\n");
        script.append("apt-get upgrade -y\n\n");

        // Install dependencies
        script.append("# Install dependencies\n");
        script.append("apt-get install -y curl wget gnupg2 lsb-release software-properties-common ");
        script.append("python3 python3-pip python3-venv\n\n");

        // Install PostgreSQL
        script.append("# Install PostgreSQL ").append(config.getPostgresVersion()).append("\n");
        script.append("sh -c 'echo \"deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main\" > /etc/apt/sources.list.d/pgdg.list'\n");
        script.append("wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -\n");
        script.append("apt-get update\n");
        script.append("apt-get install -y postgresql-").append(config.getPostgresVersion());
        script.append(" postgresql-client-").append(config.getPostgresVersion()).append("\n");
        script.append("systemctl stop postgresql\n");
        script.append("systemctl disable postgresql\n\n");

        // Install etcd
        script.append("# Install etcd\n");
        script.append("ETCD_VER=v3.5.11\n");
        script.append("curl -L https://github.com/etcd-io/etcd/releases/download/${ETCD_VER}/etcd-${ETCD_VER}-linux-amd64.tar.gz -o /tmp/etcd.tar.gz\n");
        script.append("tar xzf /tmp/etcd.tar.gz -C /tmp\n");
        script.append("mv /tmp/etcd-${ETCD_VER}-linux-amd64/etcd* /usr/local/bin/\n");
        script.append("rm -rf /tmp/etcd*\n");
        script.append("mkdir -p /var/lib/etcd\n\n");

        // Install Patroni
        script.append("# Install Patroni\n");
        script.append("pip3 install patroni[etcd3] psycopg2-binary --break-system-packages\n\n");

        // Create etcd service
        script.append("# Create etcd systemd service\n");
        script.append("cat > /etc/systemd/system/etcd.service << 'ETCD_SERVICE'\n");
        script.append(generateEtcdService(config));
        script.append("ETCD_SERVICE\n\n");

        // Create Patroni config directory
        script.append("mkdir -p /etc/patroni\n\n");

        // Create Patroni config
        script.append("# Create Patroni config\n");
        script.append("cat > /etc/patroni/patroni.yml << 'PATRONI_CONFIG'\n");
        script.append(generatePatroniConfig(config));
        script.append("PATRONI_CONFIG\n\n");

        // Create Patroni service
        script.append("# Create Patroni systemd service\n");
        script.append("cat > /etc/systemd/system/patroni.service << 'PATRONI_SERVICE'\n");
        script.append(generatePatroniService());
        script.append("PATRONI_SERVICE\n\n");

        // Prepare PostgreSQL data directory
        script.append("# Prepare PostgreSQL data directory\n");
        script.append("rm -rf /var/lib/postgresql/").append(config.getPostgresVersion()).append("/main\n");
        script.append("mkdir -p /var/lib/postgresql/").append(config.getPostgresVersion()).append("/main\n");
        script.append("chown -R postgres:postgres /var/lib/postgresql/").append(config.getPostgresVersion()).append("\n\n");

        // Start services
        script.append("# Start services\n");
        script.append("systemctl daemon-reload\n");
        script.append("systemctl enable etcd\n");
        script.append("systemctl start etcd\n");
        script.append("sleep 5\n");
        script.append("systemctl enable patroni\n");
        script.append("systemctl start patroni\n\n");

        script.append("echo \"Cloud-init completed at $(date)\"\n");

        return script.toString();
    }

    private String generateEtcdService(CloudInitConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Unit]\n");
        sb.append("Description=etcd\n");
        sb.append("After=network.target\n\n");
        sb.append("[Service]\n");
        sb.append("Type=notify\n");
        sb.append("Environment=\"ETCD_NAME=").append(config.getNodeName()).append("\"\n");
        sb.append("Environment=\"ETCD_DATA_DIR=/var/lib/etcd\"\n");
        sb.append("Environment=\"ETCD_LISTEN_PEER_URLS=http://").append(config.getNodeIp()).append(":2380\"\n");
        sb.append("Environment=\"ETCD_LISTEN_CLIENT_URLS=http://").append(config.getNodeIp()).append(":2379,http://127.0.0.1:2379\"\n");
        sb.append("Environment=\"ETCD_INITIAL_ADVERTISE_PEER_URLS=http://").append(config.getNodeIp()).append(":2380\"\n");
        sb.append("Environment=\"ETCD_ADVERTISE_CLIENT_URLS=http://").append(config.getNodeIp()).append(":2379\"\n");
        sb.append("Environment=\"ETCD_INITIAL_CLUSTER=").append(config.getEtcdInitialCluster()).append("\"\n");
        sb.append("Environment=\"ETCD_INITIAL_CLUSTER_STATE=new\"\n");
        sb.append("Environment=\"ETCD_INITIAL_CLUSTER_TOKEN=").append(config.getClusterSlug()).append("-etcd\"\n");
        sb.append("ExecStart=/usr/local/bin/etcd\n");
        sb.append("Restart=always\n");
        sb.append("RestartSec=5\n");
        sb.append("LimitNOFILE=65536\n\n");
        sb.append("[Install]\n");
        sb.append("WantedBy=multi-user.target\n");
        return sb.toString();
    }

    private String generatePatroniConfig(CloudInitConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("scope: ").append(config.getClusterSlug()).append("\n");
        sb.append("namespace: /pgcluster/\n");
        sb.append("name: ").append(config.getNodeName()).append("\n\n");

        sb.append("restapi:\n");
        sb.append("  listen: 0.0.0.0:8008\n");
        sb.append("  connect_address: ").append(config.getNodeIp()).append(":8008\n\n");

        sb.append("etcd3:\n");
        sb.append("  hosts: ").append(config.getEtcdHosts()).append("\n\n");

        sb.append("bootstrap:\n");
        sb.append("  dcs:\n");
        sb.append("    ttl: 30\n");
        sb.append("    loop_wait: 10\n");
        sb.append("    retry_timeout: 10\n");
        sb.append("    maximum_lag_on_failover: 1048576\n");
        sb.append("    postgresql:\n");
        sb.append("      use_pg_rewind: true\n");
        sb.append("      use_slots: true\n");
        sb.append("      parameters:\n");
        sb.append("        max_connections: 100\n");
        sb.append("        shared_buffers: 256MB\n");
        sb.append("        effective_cache_size: 768MB\n");
        sb.append("        wal_level: replica\n");
        sb.append("        hot_standby: on\n");
        sb.append("        max_wal_senders: 10\n");
        sb.append("        max_replication_slots: 10\n\n");

        sb.append("  initdb:\n");
        sb.append("    - encoding: UTF8\n");
        sb.append("    - data-checksums\n\n");

        sb.append("  pg_hba:\n");
        sb.append("    - host replication replicator 0.0.0.0/0 md5\n");
        sb.append("    - host all all 0.0.0.0/0 md5\n\n");

        sb.append("  users:\n");
        sb.append("    postgres:\n");
        sb.append("      password: ").append(config.getPostgresPassword()).append("\n");
        sb.append("      options:\n");
        sb.append("        - superuser\n");
        sb.append("    replicator:\n");
        sb.append("      password: ").append(config.getReplicatorPassword()).append("\n");
        sb.append("      options:\n");
        sb.append("        - replication\n\n");

        sb.append("postgresql:\n");
        sb.append("  listen: 0.0.0.0:5432\n");
        sb.append("  connect_address: ").append(config.getNodeIp()).append(":5432\n");
        sb.append("  data_dir: /var/lib/postgresql/").append(config.getPostgresVersion()).append("/main\n");
        sb.append("  bin_dir: /usr/lib/postgresql/").append(config.getPostgresVersion()).append("/bin\n");
        sb.append("  pgpass: /tmp/pgpass\n");
        sb.append("  authentication:\n");
        sb.append("    replication:\n");
        sb.append("      username: replicator\n");
        sb.append("      password: ").append(config.getReplicatorPassword()).append("\n");
        sb.append("    superuser:\n");
        sb.append("      username: postgres\n");
        sb.append("      password: ").append(config.getPostgresPassword()).append("\n\n");

        sb.append("tags:\n");
        sb.append("  nofailover: false\n");
        sb.append("  noloadbalance: false\n");
        sb.append("  clonefrom: false\n");
        sb.append("  nosync: false\n");

        return sb.toString();
    }

    private String generatePatroniService() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Unit]\n");
        sb.append("Description=Patroni PostgreSQL Cluster Manager\n");
        sb.append("After=network.target etcd.service\n\n");
        sb.append("[Service]\n");
        sb.append("Type=simple\n");
        sb.append("User=postgres\n");
        sb.append("Group=postgres\n");
        sb.append("ExecStart=/usr/local/bin/patroni /etc/patroni/patroni.yml\n");
        sb.append("ExecReload=/bin/kill -HUP $MAINPID\n");
        sb.append("KillMode=process\n");
        sb.append("Restart=always\n");
        sb.append("RestartSec=5\n\n");
        sb.append("[Install]\n");
        sb.append("WantedBy=multi-user.target\n");
        return sb.toString();
    }

    @Data
    @Builder
    public static class CloudInitConfig {
        private String clusterSlug;
        private String nodeName;
        private String nodeIp;
        private String postgresVersion;
        private String postgresPassword;
        private String replicatorPassword;
        private String etcdInitialCluster; // node1=http://ip1:2380,node2=http://ip2:2380,...
        private String etcdHosts;          // ip1:2379,ip2:2379,...
        private List<String> allNodeIps;
    }
}
