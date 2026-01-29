#!/bin/bash
#
# Create Hetzner snapshot for PostgreSQL clusters
#
# This script creates a temporary server, installs Docker and required packages,
# pre-pulls all required images, then creates a snapshot for fast provisioning.
#
# Usage:
#   ./create-snapshot.sh              # Customer cluster snapshot (default)
#   ./create-snapshot.sh --cp         # Control plane snapshot
#
# Required environment variables:
#   HETZNER_API_TOKEN - Hetzner Cloud API token
#
# Optional:
#   SSH_KEY_PATH   - Path to SSH private key (default: ~/.ssh/dbaas_ed25519)
#   SNAPSHOT_NAME  - Name for the snapshot (auto-generated if not set)
#
# Note: Patroni images for PostgreSQL 14-17 are pulled from Docker Hub (denysd1/patroni).
#       The snapshot pre-pulls PG 16 (default). Other versions are pulled on-demand.
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

# Auto-load .env file if it exists (for credentials)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

if [[ -f "${PROJECT_ROOT}/.env" ]]; then
    log_info "Loading environment from ${PROJECT_ROOT}/.env"
    set -a
    source "${PROJECT_ROOT}/.env"
    set +a
elif [[ -f "${SCRIPT_DIR}/.env" ]]; then
    log_info "Loading environment from ${SCRIPT_DIR}/.env"
    set -a
    source "${SCRIPT_DIR}/.env"
    set +a
fi

# Parse arguments
SNAPSHOT_TYPE="customer"  # default
while [[ $# -gt 0 ]]; do
    case $1 in
        --cp|--control-plane)
            SNAPSHOT_TYPE="control-plane"
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            echo "Usage: $0 [--cp|--control-plane]"
            exit 1
            ;;
    esac
done

# Configuration based on type
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/dbaas_ed25519}"

if [[ "$SNAPSHOT_TYPE" == "control-plane" ]]; then
    SNAPSHOT_NAME="${SNAPSHOT_NAME:-pgcluster-cp-v1}"
    SERVER_NAME="cp-snapshot-builder-$(date +%s)"
    log_info "Creating CONTROL PLANE snapshot"
else
    SNAPSHOT_NAME="${SNAPSHOT_NAME:-pgcluster-node-v2}"
    SERVER_NAME="snapshot-builder-$(date +%s)"
    log_info "Creating CUSTOMER CLUSTER snapshot (supports PostgreSQL 14-17)"
fi

SERVER_TYPE="cx23"
LOCATION="fsn1"

# Validate
if [[ -z "$HETZNER_API_TOKEN" ]]; then
    log_error "HETZNER_API_TOKEN is required"
    exit 1
fi

if [[ ! -f "$SSH_KEY_PATH" ]]; then
    log_error "SSH private key not found at $SSH_KEY_PATH"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/hetzner.sh"

# Step 1: Get or create SSH key
log_info "Step 1: Setting up SSH key..."
SSH_KEY_ID=$(hetzner_ensure_ssh_key "dbaas-control-plane" "$SSH_KEY_PATH.pub")
log_info "SSH Key ID: $SSH_KEY_ID"

# Step 2: Create temporary server
log_info "Step 2: Creating temporary server..."
result=$(hetzner_create_server "$SERVER_NAME" "$SERVER_TYPE" "$LOCATION" "$SSH_KEY_ID")
SERVER_ID=$(echo "$result" | cut -d'|' -f1)
SERVER_IP=$(echo "$result" | cut -d'|' -f2)
log_info "Server created: ID=$SERVER_ID, IP=$SERVER_IP"

# Cleanup function
cleanup() {
    log_warn "Cleaning up..."
    if [[ -n "$SERVER_ID" ]]; then
        log_info "Deleting server $SERVER_ID..."
        curl -s -X DELETE "https://api.hetzner.cloud/v1/servers/$SERVER_ID" \
            -H "Authorization: Bearer $HETZNER_API_TOKEN" > /dev/null
    fi
}
trap cleanup EXIT

# Step 3: Wait for SSH
log_info "Step 3: Waiting for SSH..."
sleep 30
until ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=5 -i "$SSH_KEY_PATH" root@"$SERVER_IP" "echo ready" 2>/dev/null; do
    sleep 5
done
log_info "SSH is ready"

# Step 4: Configure server (type-specific)
log_info "Step 4: Installing Docker and configuring server..."

# Common configuration (both types)
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -i "$SSH_KEY_PATH" root@"$SERVER_IP" << 'COMMON_SCRIPT'
set -e

echo "=== Installing Docker ==="
curl -fsSL https://get.docker.com | sh
systemctl enable docker
systemctl start docker

echo "=== Creating directories ==="
mkdir -p /opt/pgcluster/scripts
mkdir -p /data/postgresql
mkdir -p /data/etcd
chmod 700 /data/postgresql  # PostgreSQL requires strict permissions
chown -R 999:999 /data/postgresql  # postgres user in container
COMMON_SCRIPT

# Type-specific configuration
if [[ "$SNAPSHOT_TYPE" == "control-plane" ]]; then
    log_info "Installing control plane packages (nginx, certbot)..."
    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -i "$SSH_KEY_PATH" root@"$SERVER_IP" << 'CP_SCRIPT'
set -e

# Control plane specific directories
mkdir -p /opt/dbaas/{config,logs}

echo "=== Installing tools and nginx ==="
apt-get update
apt-get install -y jq curl nginx certbot python3-certbot-nginx

# Configure basic nginx (will be overwritten during actual setup)
cat > /etc/nginx/sites-available/default << 'NGINX_CONF'
server {
    listen 80 default_server;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX_CONF

systemctl enable nginx
systemctl restart nginx
CP_SCRIPT
else
    log_info "Installing customer cluster packages..."
    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -i "$SSH_KEY_PATH" root@"$SERVER_IP" << 'CUSTOMER_SCRIPT'
set -e

echo "=== Adding PostgreSQL APT repository ==="
# Use modern keyring approach instead of deprecated apt-key
mkdir -p /etc/apt/keyrings
wget --quiet -O /etc/apt/keyrings/postgresql.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc
echo "deb [signed-by=/etc/apt/keyrings/postgresql.asc] http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list

echo "=== Installing tools and pgBackRest ==="
apt-get update
apt-get install -y jq curl pgbackrest

echo "=== Creating pgBackRest directories ==="
mkdir -p /etc/pgbackrest
mkdir -p /var/lib/pgbackrest
mkdir -p /var/log/pgbackrest
mkdir -p /var/spool/pgbackrest
chmod 750 /etc/pgbackrest
CUSTOMER_SCRIPT
fi

# Pull Docker images and cleanup (both types)
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -i "$SSH_KEY_PATH" root@"$SERVER_IP" << 'IMAGES_SCRIPT'
set -e

echo "=== Pre-pulling Docker images ==="
docker pull quay.io/coreos/etcd:v3.5.11
docker pull prom/node-exporter:v1.7.0
docker pull prometheuscommunity/postgres-exporter:v0.15.0
docker pull edoburu/pgbouncer:v1.23.1-p3

# Patroni images (denysd1/patroni:14-17) are pulled on-demand from Docker Hub
# This keeps the snapshot small and version-agnostic

echo "=== Cleanup ==="
apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/*
history -c

echo "=== Done ==="
IMAGES_SCRIPT

log_info "Server configured"

# Step 6: Shutdown and create snapshot
log_info "Step 6: Creating snapshot..."

# Shutdown server
curl -s -X POST "https://api.hetzner.cloud/v1/servers/$SERVER_ID/actions/shutdown" \
    -H "Authorization: Bearer $HETZNER_API_TOKEN" \
    -H "Content-Type: application/json" > /dev/null

log_info "Waiting for server to shutdown..."
while true; do
    STATUS=$(curl -s "https://api.hetzner.cloud/v1/servers/$SERVER_ID" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" | jq -r '.server.status')
    if [[ "$STATUS" == "off" ]]; then
        break
    fi
    sleep 5
done

# Create snapshot
log_info "Creating snapshot '$SNAPSHOT_NAME'..."
SNAPSHOT_RESPONSE=$(curl -s -X POST "https://api.hetzner.cloud/v1/servers/$SERVER_ID/actions/create_image" \
    -H "Authorization: Bearer $HETZNER_API_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"type\": \"snapshot\", \"description\": \"$SNAPSHOT_NAME\"}")

SNAPSHOT_ID=$(echo "$SNAPSHOT_RESPONSE" | jq -r '.image.id')
ACTION_ID=$(echo "$SNAPSHOT_RESPONSE" | jq -r '.action.id')

log_info "Snapshot creation started, waiting for completion..."

# Wait for snapshot to complete
while true; do
    ACTION_STATUS=$(curl -s "https://api.hetzner.cloud/v1/actions/$ACTION_ID" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" | jq -r '.action.status')
    if [[ "$ACTION_STATUS" == "success" ]]; then
        break
    elif [[ "$ACTION_STATUS" == "error" ]]; then
        log_error "Snapshot creation failed!"
        exit 1
    fi
    sleep 5
done

# Done (cleanup will delete the server)
trap - EXIT

log_info "Deleting temporary server..."
curl -s -X DELETE "https://api.hetzner.cloud/v1/servers/$SERVER_ID" \
    -H "Authorization: Bearer $HETZNER_API_TOKEN" > /dev/null

# Output based on type
echo ""
echo -e "${GREEN}=========================================="
if [[ "$SNAPSHOT_TYPE" == "control-plane" ]]; then
    echo "Control Plane Snapshot Created Successfully!"
else
    echo "Customer Cluster Snapshot Created Successfully!"
fi
echo "==========================================${NC}"
echo ""
echo "Snapshot ID: $SNAPSHOT_ID"
echo "Snapshot Name: $SNAPSHOT_NAME"
echo ""

if [[ "$SNAPSHOT_TYPE" == "control-plane" ]]; then
    echo "This snapshot includes:"
    echo "  - Docker"
    echo "  - etcd, Patroni, node-exporter, postgres-exporter images"
    echo "  - nginx + certbot (for SSL)"
    echo "  - /opt/dbaas directory structure"
    echo ""
    echo "Add to your .env file:"
    echo ""
    echo "  CP_SNAPSHOT_ID=$SNAPSHOT_ID"
    echo ""
    echo "Then run: ./scripts/setup-control-plane-docker.sh"
else
    echo "This snapshot includes:"
    echo "  - Docker"
    echo "  - Pre-pulled images: etcd, node-exporter, postgres-exporter, pgbouncer"
    echo "  - pgBackRest (backup tool)"
    echo "  - /data/postgresql, /data/etcd directories"
    echo ""
    echo "Patroni images (PostgreSQL 14-17) are pulled on-demand from Docker Hub."
    echo ""
    echo "Add to your .env file:"
    echo ""
    echo "  CUSTOMER_SNAPSHOT_ID=$SNAPSHOT_ID"
    echo ""
    echo "This ID is required when deploying the API (Step 4 in docs/initial-setup.md)"
fi
echo ""
