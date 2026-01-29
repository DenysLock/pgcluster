#!/bin/bash
#
# DBaaS Control Plane Setup Script (Docker-based)
# Sets up a 3-node control plane using Docker containers for all services
#
# Usage: ./setup-control-plane-docker.sh
#
# Required environment variables:
#   HETZNER_API_TOKEN        - Hetzner Cloud API token
#   CLOUDFLARE_API_TOKEN     - Cloudflare API token
#   CLOUDFLARE_ZONE_ID   - Cloudflare zone ID
#   DOMAIN               - Base domain (e.g., pgcluster.com)
#
# SSL Certificate (choose one):
#   Option 1 - Cloudflare Origin Certificate (recommended):
#     CLOUDFLARE_ORIGIN_CERT - Base64 encoded origin certificate
#     CLOUDFLARE_ORIGIN_KEY  - Base64 encoded private key
#   Option 2 - Let's Encrypt (requires disabling CF proxy):
#     ADMIN_EMAIL          - Email for Let's Encrypt SSL certificates
#
# Required (from Step 2 - create snapshots):
#   CP_SNAPSHOT_ID       - Control plane snapshot ID (from ./scripts/create-snapshot.sh --cp)
#
# Optional:
#   SSH_KEY_PATH         - Path to SSH private key (default: ~/.ssh/dbaas_ed25519)
#   CUSTOMER_SNAPSHOT_ID - Customer cluster snapshot (for output only, required for API in Step 4)
#   DB_PASSWORD          - Database user password (generated if not set)
#   REPLICATOR_PASSWORD  - Replication user password (generated if not set)
#   POSTGRES_PASSWORD    - PostgreSQL superuser password (generated if not set)
#   GRAFANA_PASSWORD     - Grafana admin password (generated if not set)
#   JWT_SECRET           - API JWT signing secret (generated if not set)
#   FIELD_ENCRYPTION_KEY - AES-256 key for password encryption (generated if not set)
#   INTERNAL_API_KEY     - API key for /internal/* endpoints (generated if not set)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Load library functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Auto-load .env file if it exists (for credentials)
if [[ -f "${PROJECT_ROOT}/.env" ]]; then
    log_info "Loading environment from ${PROJECT_ROOT}/.env"
    set -a  # automatically export all variables
    source "${PROJECT_ROOT}/.env"
    set +a
elif [[ -f "${SCRIPT_DIR}/.env" ]]; then
    log_info "Loading environment from ${SCRIPT_DIR}/.env"
    set -a
    source "${SCRIPT_DIR}/.env"
    set +a
fi
source "${SCRIPT_DIR}/lib/hetzner.sh"
source "${SCRIPT_DIR}/lib/cloudflare.sh"

# Configuration
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/dbaas_ed25519}"
CP_SNAPSHOT_ID="${CP_SNAPSHOT_ID:?CP_SNAPSHOT_ID required. Create with: ./scripts/create-snapshot.sh --cp}"
CUSTOMER_SNAPSHOT_ID="${CUSTOMER_SNAPSHOT_ID:-}"  # Optional here, required for API deployment
CP_LOCATIONS=("nbg1" "fsn1" "hel1")  # Nuremberg, Falkenstein, Helsinki
CP_NAMES=("dbaas-cp-1" "dbaas-cp-2" "dbaas-cp-3")
SERVER_TYPE="cx23"

# Generate passwords/secrets if not provided
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -base64 24)}"
REPLICATOR_PASSWORD="${REPLICATOR_PASSWORD:-$(openssl rand -base64 24)}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(openssl rand -base64 24)}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-$(openssl rand -base64 16)}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"
FIELD_ENCRYPTION_KEY="${FIELD_ENCRYPTION_KEY:-$(openssl rand -base64 32)}"
INTERNAL_API_KEY="${INTERNAL_API_KEY:-$(openssl rand -hex 32)}"

# Validate required environment variables
validate_env() {
    local missing=0

    if [[ -z "$HETZNER_API_TOKEN" ]]; then
        log_error "HETZNER_API_TOKEN is required"
        missing=1
    fi

    if [[ -z "$CLOUDFLARE_API_TOKEN" ]]; then
        log_error "CLOUDFLARE_API_TOKEN is required"
        missing=1
    fi

    if [[ -z "$CLOUDFLARE_ZONE_ID" ]]; then
        log_error "CLOUDFLARE_ZONE_ID is required"
        missing=1
    fi

    if [[ -z "$DOMAIN" ]]; then
        log_error "DOMAIN is required (e.g., pgcluster.com)"
        missing=1
    fi

    # SSL: Either Origin Cert OR Admin Email (for Let's Encrypt) is required
    if [[ -z "$CLOUDFLARE_ORIGIN_CERT" || -z "$CLOUDFLARE_ORIGIN_KEY" ]]; then
        if [[ -z "$ADMIN_EMAIL" ]]; then
            log_error "SSL requires either CLOUDFLARE_ORIGIN_CERT + CLOUDFLARE_ORIGIN_KEY, or ADMIN_EMAIL for Let's Encrypt"
            missing=1
        else
            log_info "Using Let's Encrypt for SSL (ADMIN_EMAIL provided)"
        fi
    else
        log_info "Using Cloudflare Origin Certificate for SSL"
    fi

    if [[ ! -f "$SSH_KEY_PATH" ]]; then
        log_error "SSH private key not found at $SSH_KEY_PATH"
        missing=1
    fi

    if [[ $missing -eq 1 ]]; then
        exit 1
    fi
}

# SSH helper
ssh_cmd() {
    local ip="$1"
    local cmd="$2"
    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -i "$SSH_KEY_PATH" root@"$ip" "$cmd"
}

# Main setup function
main() {
    log_info "Starting DBaaS Control Plane Setup (Docker-based)"
    log_info "Domain: $DOMAIN"
    log_info "Snapshot: $CP_SNAPSHOT_ID"

    validate_env

    # Step 1: Create or get SSH key in Hetzner
    log_info "Step 1: Setting up SSH key in Hetzner..."
    SSH_KEY_ID=$(hetzner_ensure_ssh_key "dbaas-control-plane" "$SSH_KEY_PATH.pub")
    log_info "SSH Key ID: $SSH_KEY_ID"

    # Step 2: Create 3 VPS nodes from snapshot
    log_info "Step 2: Creating VPS nodes from snapshot..."
    declare -a SERVER_IDS
    declare -a SERVER_IPS

    for i in 0 1 2; do
        log_info "Creating ${CP_NAMES[$i]} in ${CP_LOCATIONS[$i]} from snapshot..."
        result=$(hetzner_create_server_from_snapshot "${CP_NAMES[$i]}" "$SERVER_TYPE" "${CP_LOCATIONS[$i]}" "$SSH_KEY_ID" "$CP_SNAPSHOT_ID")
        if [[ $? -ne 0 ]]; then
            log_error "Failed to create server ${CP_NAMES[$i]}"
            exit 1
        fi
        SERVER_IDS[$i]=$(echo "$result" | cut -d'|' -f1)
        SERVER_IPS[$i]=$(echo "$result" | cut -d'|' -f2)
        log_info "Created ${CP_NAMES[$i]}: ID=${SERVER_IDS[$i]}, IP=${SERVER_IPS[$i]}"
    done

    # Step 3: Create DNS record (pointing to first node, will be updated by callback on failover)
    # Using proxied=true for DDoS protection and SSL. When DNS is updated via callback,
    # Cloudflare instantly routes to new origin (no client DNS caching issues).
    log_info "Step 3: Creating DNS record (DNS-based failover, no Floating IP)..."
    cloudflare_create_record "api" "${SERVER_IPS[0]}" true
    log_info "DNS record created: api.$DOMAIN -> ${SERVER_IPS[0]} (proxied via Cloudflare)"

    # Step 4: Wait for servers to be ready
    log_info "Step 4: Waiting for servers to be ready..."
    sleep 20  # Snapshot-based servers boot faster

    for ip in "${SERVER_IPS[@]}"; do
        log_info "Waiting for $ip to be reachable..."
        until ssh_cmd "$ip" "echo ready" 2>/dev/null; do
            sleep 5
        done
        log_info "$ip is ready"
    done

    # Step 5: Configure each node with Docker setup
    log_info "Step 5: Configuring nodes with Docker..."

    for i in 0 1 2; do
        local ip="${SERVER_IPS[$i]}"
        local node_name="cp$((i+1))"
        local server_id="${SERVER_IDS[$i]}"

        log_info "Configuring ${CP_NAMES[$i]} ($ip)..."

        # Create Patroni config
        log_info "  Creating Patroni config..."
        ssh_cmd "$ip" "cat > /opt/pgcluster/patroni.yml << 'EOF'
scope: dbaas-cluster
namespace: /dbaas/
name: ${node_name}

restapi:
  listen: 0.0.0.0:8008
  connect_address: ${ip}:8008

etcd3:
  hosts: ${SERVER_IPS[0]}:2379,${SERVER_IPS[1]}:2379,${SERVER_IPS[2]}:2379

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576
    postgresql:
      use_pg_rewind: true
      use_slots: true
      parameters:
        max_connections: 100
        shared_buffers: 256MB
        effective_cache_size: 768MB
        maintenance_work_mem: 64MB
        checkpoint_completion_target: 0.9
        wal_level: replica
        hot_standby: on
        max_wal_senders: 10
        max_replication_slots: 10
        wal_keep_size: 128MB
        logging_collector: off
        log_destination: stderr

  initdb:
    - encoding: UTF8
    - data-checksums

  pg_hba:
    - host replication replicator 0.0.0.0/0 md5
    - host all all 0.0.0.0/0 md5

  users:
    dbaas:
      password: ${DB_PASSWORD}
      options:
        - createrole
        - createdb
    replicator:
      password: ${REPLICATOR_PASSWORD}
      options:
        - replication

postgresql:
  callbacks:
    on_start: /etc/patroni/dns_callback.sh
    on_role_change: /etc/patroni/dns_callback.sh
  listen: 0.0.0.0:5432
  connect_address: ${ip}:5432
  data_dir: /var/lib/postgresql/data
  bin_dir: /usr/lib/postgresql/16/bin
  pgpass: /tmp/pgpass
  authentication:
    replication:
      username: replicator
      password: ${REPLICATOR_PASSWORD}
    superuser:
      username: postgres
      password: ${POSTGRES_PASSWORD}
    rewind:
      username: postgres
      password: ${POSTGRES_PASSWORD}

tags:
  nofailover: false
  noloadbalance: false
  clonefrom: false
  nosync: false
EOF"

        # Create DNS callback script (pure DNS-based failover, no Floating IP)
        log_info "  Creating DNS callback script..."
        ssh_cmd "$ip" "cat > /opt/pgcluster/dns_callback.sh << 'EOFCB'
#!/bin/bash
# DNS Callback Script for Control Plane Patroni
# Updates Cloudflare DNS when this node becomes the leader

ACTION=\"\$1\"
ROLE=\"\$2\"
CLUSTER=\"\$3\"

# DNS configuration
MY_IP=\"${ip}\"
CLOUDFLARE_API_TOKEN=\"${CLOUDFLARE_API_TOKEN}\"
CLOUDFLARE_ZONE_ID=\"${CLOUDFLARE_ZONE_ID}\"
DNS_HOSTNAME=\"api.${DOMAIN}\"

LOG=\"/var/log/patroni-dns-callback.log\"

log() {
    echo \"[\$(date '+%Y-%m-%d %H:%M:%S')] \$*\" >> \$LOG 2>/dev/null || true
    logger -t \"patroni-dns-callback\" \"\$*\" 2>/dev/null || true
}

log \"Callback: action=\$ACTION, role=\$ROLE, cluster=\$CLUSTER\"

if [ \"\$ROLE\" == \"master\" ] || [ \"\$ROLE\" == \"leader\" ] || [ \"\$ROLE\" == \"primary\" ]; then
    log \"This node is now the leader. Updating DNS record \${DNS_HOSTNAME} to \${MY_IP}...\"

    # Find DNS record ID
    DNS_RECORD_ID=\$(curl -s -X GET \"https://api.cloudflare.com/client/v4/zones/\${CLOUDFLARE_ZONE_ID}/dns_records?name=\${DNS_HOSTNAME}\" \\
        -H \"Authorization: Bearer \${CLOUDFLARE_API_TOKEN}\" \\
        -H \"Content-Type: application/json\" | grep -o '\"id\":\"[^\"]*\"' | head -1 | cut -d'\"' -f4)

    if [ -n \"\$DNS_RECORD_ID\" ]; then
        RESPONSE=\$(curl -s -X PATCH \"https://api.cloudflare.com/client/v4/zones/\${CLOUDFLARE_ZONE_ID}/dns_records/\${DNS_RECORD_ID}\" \\
            -H \"Authorization: Bearer \${CLOUDFLARE_API_TOKEN}\" \\
            -H \"Content-Type: application/json\" \\
            -d \"{\\\"content\\\": \\\"\${MY_IP}\\\"}\")

        if echo \"\$RESPONSE\" | grep -q '\"success\":true'; then
            log \"SUCCESS: DNS updated \${DNS_HOSTNAME} -> \${MY_IP}\"
        else
            log \"ERROR: DNS update failed: \$RESPONSE\"
        fi
    else
        log \"ERROR: Could not find DNS record ID for \${DNS_HOSTNAME}\"
    fi
else
    log \"Role is \$ROLE (not leader), no DNS update needed\"
fi

exit 0
EOFCB"
        ssh_cmd "$ip" "chmod +x /opt/pgcluster/dns_callback.sh"

        # Create docker-compose.yml
        log_info "  Creating docker-compose.yml..."
        ssh_cmd "$ip" "cat > /opt/pgcluster/docker-compose.yml << 'EOF'
services:
  etcd:
    image: quay.io/coreos/etcd:v3.5.11
    container_name: etcd
    restart: unless-stopped
    network_mode: host
    volumes:
      - /data/etcd:/etcd-data
    environment:
      - ETCD_NAME=${node_name}
      - ETCD_DATA_DIR=/etcd-data
      - ETCD_LISTEN_PEER_URLS=http://${ip}:2380
      - ETCD_LISTEN_CLIENT_URLS=http://${ip}:2379,http://127.0.0.1:2379
      - ETCD_INITIAL_ADVERTISE_PEER_URLS=http://${ip}:2380
      - ETCD_ADVERTISE_CLIENT_URLS=http://${ip}:2379
      - ETCD_INITIAL_CLUSTER=cp1=http://${SERVER_IPS[0]}:2380,cp2=http://${SERVER_IPS[1]}:2380,cp3=http://${SERVER_IPS[2]}:2380
      - ETCD_INITIAL_CLUSTER_STATE=new
      - ETCD_INITIAL_CLUSTER_TOKEN=dbaas-etcd-cluster
    healthcheck:
      test: [\"CMD\", \"etcdctl\", \"endpoint\", \"health\"]
      interval: 10s
      timeout: 5s
      retries: 3

  patroni:
    image: denysd1/patroni:16
    container_name: patroni
    restart: unless-stopped
    network_mode: host
    cap_add:
      - NET_ADMIN
    depends_on:
      etcd:
        condition: service_healthy
    volumes:
      - /data/postgresql:/var/lib/postgresql/data
      - /opt/pgcluster/patroni.yml:/etc/patroni/patroni.yml:ro
      - /opt/pgcluster/dns_callback.sh:/etc/patroni/dns_callback.sh:ro
      - /var/log:/var/log
    environment:
      - PATRONI_NAME=${node_name}
      - PATRONI_RESTAPI_CONNECT_ADDRESS=${ip}:8008
      - PATRONI_POSTGRESQL_CONNECT_ADDRESS=${ip}:5432
    healthcheck:
      test: [\"CMD\", \"curl\", \"-f\", \"http://localhost:8008/health\"]
      interval: 10s
      timeout: 5s
      retries: 3

  node-exporter:
    image: prom/node-exporter:v1.7.0
    container_name: node-exporter
    restart: unless-stopped
    network_mode: host
    pid: host
    volumes:
      - /:/host:ro,rslave
    command:
      - '--path.rootfs=/host'
      - '--web.listen-address=:9100'

  postgres-exporter:
    image: prometheuscommunity/postgres-exporter:v0.15.0
    container_name: postgres-exporter
    restart: unless-stopped
    network_mode: host
    depends_on:
      patroni:
        condition: service_healthy
    environment:
      - DATA_SOURCE_NAME=postgresql://dbaas:${DB_PASSWORD}@127.0.0.1:5432/dbaas?sslmode=disable
    command:
      - '--web.listen-address=:9187'
EOF"

        log_info "${CP_NAMES[$i]} configured"
    done

    # Step 5b: Pull Patroni image on all nodes from Docker Hub
    log_info "Step 5b: Pulling Patroni image on all nodes..."

    for ip in "${SERVER_IPS[@]}"; do
        log_info "  Pulling denysd1/patroni:16 on $ip..."
        ssh_cmd "$ip" 'docker pull denysd1/patroni:16' &
    done
    wait
    log_info "Patroni images pulled on all nodes"

    # Step 6: Start Docker containers on all nodes
    # Important: Start etcd on ALL nodes first (in parallel), then patroni
    log_info "Step 6: Starting Docker containers..."

    # Phase 1: Start etcd on all nodes in parallel
    log_info "Starting etcd on all nodes..."
    for ip in "${SERVER_IPS[@]}"; do
        log_info "  Starting etcd on $ip..."
        ssh_cmd "$ip" "cd /opt/pgcluster && docker compose up -d etcd" &
    done
    wait  # Wait for all etcd starts

    # Wait for etcd cluster to form
    log_info "Waiting for etcd cluster to form..."
    sleep 15

    # Phase 2: Start remaining services on all nodes
    log_info "Starting Patroni and other services..."
    for ip in "${SERVER_IPS[@]}"; do
        log_info "  Starting services on $ip..."
        ssh_cmd "$ip" "cd /opt/pgcluster && docker compose up -d"
    done

    # Check etcd health
    log_info "Checking etcd cluster health..."
    ssh_cmd "${SERVER_IPS[0]}" "docker exec etcd etcdctl endpoint health --cluster" || log_warn "etcd health check failed"

    # Wait for Patroni to elect leader
    log_info "Waiting for Patroni leader election..."
    local retries=60
    local leader_ip=""
    while true; do
        local status=$(ssh_cmd "${SERVER_IPS[0]}" "curl -s http://localhost:8008/cluster" 2>/dev/null || echo "")
        if echo "$status" | grep -qE '"role":\s*"(leader|primary|master)"'; then
            log_info "Patroni cluster has elected a leader!"
            # Find the leader IP
            for ip in "${SERVER_IPS[@]}"; do
                local role=$(ssh_cmd "$ip" "curl -s http://localhost:8008/patroni" 2>/dev/null | grep -o '"role":\s*"[^"]*"' | head -1 || echo "")
                if echo "$role" | grep -qE '"role":\s*"(leader|primary|master)"'; then
                    leader_ip="$ip"
                    log_info "Leader is at $leader_ip"
                    break
                fi
            done
            break
        fi
        retries=$((retries - 1))
        if [[ $retries -le 0 ]]; then
            log_warn "Patroni leader election timeout - check logs"
            break
        fi
        sleep 5
    done

    # Step 6b: Create database user and database
    if [[ -n "$leader_ip" ]]; then
        log_info "Step 7b: Creating dbaas user and database..."
        ssh_cmd "$leader_ip" "docker exec patroni psql -U postgres -c \"CREATE USER dbaas WITH PASSWORD '$DB_PASSWORD' CREATEROLE CREATEDB;\" 2>/dev/null || true"
        ssh_cmd "$leader_ip" "docker exec patroni psql -U postgres -c 'CREATE DATABASE dbaas OWNER dbaas;' 2>/dev/null || true"
        log_info "Database user and database created"
    else
        log_warn "No leader found, skipping database user creation"
    fi

    # Step 7: Setup SSL certificates
    log_info "Step 7: Setting up SSL certificates..."

    if [[ -n "$CLOUDFLARE_ORIGIN_CERT" && -n "$CLOUDFLARE_ORIGIN_KEY" ]]; then
        # Use Cloudflare Origin Certificate (recommended)
        log_info "Installing Cloudflare Origin Certificate on all nodes..."
        for ip in "${SERVER_IPS[@]}"; do
            log_info "  Installing certificate on $ip..."

            # Decode and install certificate
            ssh_cmd "$ip" "echo '$CLOUDFLARE_ORIGIN_CERT' | base64 -d > /etc/ssl/certs/cloudflare-origin.pem"
            ssh_cmd "$ip" "echo '$CLOUDFLARE_ORIGIN_KEY' | base64 -d > /etc/ssl/private/cloudflare-origin.key"
            ssh_cmd "$ip" "chmod 600 /etc/ssl/private/cloudflare-origin.key"

            # Configure nginx for HTTPS with Origin Certificate
            # Using a simple heredoc with proper quoting
            ssh_cmd "$ip" 'cat > /etc/nginx/sites-available/default << '\''NGINXEOF'\''
server {
    listen 80;
    server_name api.'"${DOMAIN}"';
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name api.'"${DOMAIN}"';

    ssl_certificate /etc/ssl/certs/cloudflare-origin.pem;
    ssl_certificate_key /etc/ssl/private/cloudflare-origin.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINXEOF
nginx -t && systemctl reload nginx'
            log_info "  Certificate installed on $ip"
        done
        log_info "Cloudflare Origin Certificate installed on all nodes"
    else
        # Fallback to Let's Encrypt (requires CF proxy disabled)
        log_warn "Using Let's Encrypt - make sure Cloudflare proxy is DISABLED for api.$DOMAIN"
        for ip in "${SERVER_IPS[@]}"; do
            log_info "Getting SSL certificate on $ip..."
            ssh_cmd "$ip" "certbot --nginx -d api.$DOMAIN --non-interactive --agree-tos -m $ADMIN_EMAIL --redirect" || log_warn "SSL setup failed on $ip (may need manual intervention)"
        done
    fi

    # Step 8: Generate and distribute SSH key for API provisioning
    log_info "Step 8: Setting up SSH key for API provisioning..."

    # Create directory on all nodes
    for ip in "${SERVER_IPS[@]}"; do
        ssh_cmd "$ip" "mkdir -p /opt/dbaas/ssh"
    done

    # Generate key on first node
    ssh_cmd "${SERVER_IPS[0]}" "ssh-keygen -t ed25519 -f /opt/dbaas/ssh/id_rsa -N '' -C 'dbaas-provisioning'"

    # Set ownership to 1000:1000 (appuser in container)
    ssh_cmd "${SERVER_IPS[0]}" "chown 1000:1000 /opt/dbaas/ssh/id_rsa"

    # Get public key
    PROVISIONING_PUBKEY=$(ssh_cmd "${SERVER_IPS[0]}" "cat /opt/dbaas/ssh/id_rsa.pub")

    # Register with Hetzner
    PROVISIONING_KEY_ID=$(curl -s -X POST "https://api.hetzner.cloud/v1/ssh_keys" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"name\": \"dbaas-provisioning-$(date +%s)\", \"public_key\": \"$PROVISIONING_PUBKEY\"}" | jq -r '.ssh_key.id')
    log_info "Provisioning SSH Key registered with Hetzner: $PROVISIONING_KEY_ID"

    # Copy private key to other nodes
    PRIVATE_KEY=$(ssh_cmd "${SERVER_IPS[0]}" "cat /opt/dbaas/ssh/id_rsa")
    for ip in "${SERVER_IPS[@]:1}"; do
        ssh_cmd "$ip" "echo '$PRIVATE_KEY' > /opt/dbaas/ssh/id_rsa"
        ssh_cmd "$ip" "chmod 600 /opt/dbaas/ssh/id_rsa"
        ssh_cmd "$ip" "chown 1000:1000 /opt/dbaas/ssh/id_rsa"
    done
    log_info "SSH key distributed to all nodes with correct ownership (1000:1000)"

    # Step 9: Deploy API to all nodes
    log_info "Step 9: API deployment instructions..."
    log_info ""
    log_info "Build the API image on your local machine:"
    log_info ""
    log_info "  cd backend"
    log_info "  ./mvnw package -DskipTests"
    log_info "  docker build --platform linux/amd64 -t pgcluster-api:latest ."
    log_info "  docker save pgcluster-api:latest | gzip > /tmp/pgcluster-api.tar.gz"
    log_info "  cd .."
    log_info ""
    log_info "Upload to all control plane nodes:"
    log_info ""
    for ip in "${SERVER_IPS[@]}"; do
        log_info "  scp -i $SSH_KEY_PATH /tmp/pgcluster-api.tar.gz root@$ip:/opt/dbaas/"
    done
    log_info ""
    for ip in "${SERVER_IPS[@]}"; do
        log_info "  ssh -i $SSH_KEY_PATH root@$ip 'docker load < /opt/dbaas/pgcluster-api.tar.gz'"
    done
    log_info ""
    log_info "Start API container on EACH node with the following command:"
    log_info ""
    log_info "  ssh -i $SSH_KEY_PATH root@<NODE_IP> 'docker run -d --name pgcluster-api \\"
    log_info "    --restart=unless-stopped --network host \\"
    log_info "    -v /opt/dbaas/ssh/id_rsa:/home/appuser/.ssh/id_rsa:ro \\"
    log_info "    -e DATABASE_URL=jdbc:postgresql://localhost:5432/dbaas \\"
    log_info "    -e DATABASE_USER=dbaas \\"
    log_info "    -e DATABASE_PASSWORD=\"$DB_PASSWORD\" \\"
    log_info "    -e JWT_SECRET=\"$JWT_SECRET\" \\"
    log_info "    -e FIELD_ENCRYPTION_KEY=\"$FIELD_ENCRYPTION_KEY\" \\"
    log_info "    -e INTERNAL_API_KEY=\"$INTERNAL_API_KEY\" \\"
    log_info "    -e HETZNER_API_TOKEN=\"$HETZNER_API_TOKEN\" \\"
    log_info "    -e HETZNER_SSH_KEY_IDS=\"$PROVISIONING_KEY_ID\" \\"
    log_info "    -e CUSTOMER_SNAPSHOT_ID=\"${CUSTOMER_SNAPSHOT_ID:-REPLACE_WITH_SNAPSHOT_ID}\" \\"
    log_info "    -e CLOUDFLARE_API_TOKEN=\"$CLOUDFLARE_API_TOKEN\" \\"
    log_info "    -e CLOUDFLARE_ZONE_ID=\"$CLOUDFLARE_ZONE_ID\" \\"
    log_info "    -e CLUSTER_BASE_DOMAIN=\"db.$DOMAIN\" \\"
    log_info "    pgcluster-api:latest'"
    log_info ""
    log_info "Replace <NODE_IP> with each node's IP: ${SERVER_IPS[0]}, ${SERVER_IPS[1]}, ${SERVER_IPS[2]}"

    # Summary
    echo ""
    log_info "=========================================="
    log_info "Control Plane Setup Complete!"
    log_info "=========================================="
    log_info ""
    log_info "Nodes:"
    for i in 0 1 2; do
        log_info "  ${CP_NAMES[$i]}: ${SERVER_IPS[$i]} (ID: ${SERVER_IDS[$i]})"
    done
    log_info ""
    log_info "DNS: api.$DOMAIN -> ${SERVER_IPS[0]} (updates automatically on failover)"
    log_info ""
    log_info "Services running (Docker containers):"
    log_info "  - etcd (cluster)"
    log_info "  - patroni (PostgreSQL HA)"
    log_info "  - node-exporter"
    log_info "  - postgres-exporter"
    log_info ""
    echo ""
    echo -e "${YELLOW}╔════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${YELLOW}║              SAVE THESE CREDENTIALS SECURELY!                      ║${NC}"
    echo -e "${YELLOW}╠════════════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${YELLOW}║${NC} Control Plane Database:                                            ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   Host: api.$DOMAIN:5432 (or current leader IP)                   ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   User: dbaas                                                      ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   Password: $DB_PASSWORD  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}                                                                    ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC} PostgreSQL Superuser:                                              ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   Password: $POSTGRES_PASSWORD  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}                                                                    ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC} Replication User:                                                  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   Password: $REPLICATOR_PASSWORD  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}                                                                    ${YELLOW}║${NC}"
    echo -e "${YELLOW}╠════════════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${YELLOW}║${NC} API Environment Variables (for Step 4 - docker run):               ${YELLOW}║${NC}"
    echo -e "${YELLOW}╠════════════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${YELLOW}║${NC}   DATABASE_PASSWORD=$DB_PASSWORD  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   JWT_SECRET=$JWT_SECRET  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   FIELD_ENCRYPTION_KEY=$FIELD_ENCRYPTION_KEY  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   INTERNAL_API_KEY=$INTERNAL_API_KEY  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   HETZNER_SSH_KEY_IDS=$PROVISIONING_KEY_ID  ${YELLOW}║${NC}"
    echo -e "${YELLOW}║${NC}   CUSTOMER_SNAPSHOT_ID=${CUSTOMER_SNAPSHOT_ID:-<run ./scripts/create-snapshot.sh>}  ${YELLOW}║${NC}"
    echo -e "${YELLOW}╚════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    log_warn "Copy the credentials above - they cannot be retrieved later!"
    echo ""
    log_info "Next steps:"
    log_info "  1. If not done: Create customer snapshot with ./scripts/create-snapshot.sh"
    log_info "  2. Build and deploy pgcluster-api image (see Step 10 instructions above)"
    log_info "  3. Test API: curl https://api.$DOMAIN/health"
    log_info "  4. Deploy frontend (optional): See docs/initial-setup.md Step 5"
}

main "$@"
