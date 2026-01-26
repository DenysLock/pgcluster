#!/bin/bash
#
# DBaaS Monitoring VPS Setup Script
# Deploys Prometheus, Grafana, and Alertmanager on a dedicated VPS
#
# Usage: ./setup-monitoring.sh
#
# Required environment variables (from .env):
#   HETZNER_API_TOKEN        - Hetzner Cloud API token
#   CUSTOMER_SNAPSHOT_ID     - Snapshot ID with Docker pre-installed
#   INTERNAL_API_KEY         - API key for Prometheus to access /internal/* endpoints
#
# Optional:
#   SSH_KEY_PATH             - Path to SSH private key (default: ~/.ssh/dbaas_ed25519)
#   GRAFANA_PASSWORD         - Grafana admin password (default: generated)
#   MONITORING_LOCATION      - Hetzner location (default: nbg1)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MONITORING_CONFIGS="${PROJECT_ROOT}/deployments/monitoring"

# Auto-load .env file
if [[ -f "${PROJECT_ROOT}/.env" ]]; then
    log_info "Loading environment from ${PROJECT_ROOT}/.env"
    set -a
    source "${PROJECT_ROOT}/.env"
    set +a
fi

# Source library functions
source "${SCRIPT_DIR}/lib/hetzner.sh"

# Configuration
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/dbaas_ed25519}"
MONITORING_LOCATION="${MONITORING_LOCATION:-nbg1}"
MONITORING_SERVER_TYPE="cx23"
MONITORING_NAME="dbaas-monitoring"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-$(openssl rand -hex 12)}"

# Validate required environment variables
validate_env() {
    local missing=0

    if [[ -z "$HETZNER_API_TOKEN" ]]; then
        log_error "HETZNER_API_TOKEN is required"
        missing=1
    fi

    if [[ -z "$CUSTOMER_SNAPSHOT_ID" ]]; then
        log_error "CUSTOMER_SNAPSHOT_ID is required"
        missing=1
    fi

    if [[ -z "$INTERNAL_API_KEY" ]]; then
        log_error "INTERNAL_API_KEY is required (needed for Prometheus to access API)"
        missing=1
    fi

    if [[ ! -f "$SSH_KEY_PATH" ]]; then
        log_error "SSH private key not found at $SSH_KEY_PATH"
        missing=1
    fi

    if [[ ! -d "$MONITORING_CONFIGS" ]]; then
        log_error "Monitoring configs not found at $MONITORING_CONFIGS"
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

# SCP helper
scp_file() {
    local src="$1"
    local ip="$2"
    local dest="$3"
    scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -i "$SSH_KEY_PATH" "$src" root@"$ip":"$dest"
}

scp_dir() {
    local src="$1"
    local ip="$2"
    local dest="$3"
    scp -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -i "$SSH_KEY_PATH" "$src" root@"$ip":"$dest"
}

# Get control plane IPs from Hetzner API
get_cp_ips() {
    local result
    result=$(curl -s "${HETZNER_API}/servers" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" | \
        jq -r '.servers[] | select(.name | startswith("dbaas-cp-")) | "\(.name):\(.public_net.ipv4.ip)"' | sort)

    if [[ -z "$result" ]]; then
        log_error "No control plane servers found (dbaas-cp-*)"
        exit 1
    fi

    echo "$result"
}

main() {
    log_info "Starting DBaaS Monitoring VPS Setup"
    log_info "Location: $MONITORING_LOCATION"

    validate_env

    # Step 1: Get SSH key ID
    log_info "Step 1: Setting up SSH key..."
    SSH_KEY_ID=$(hetzner_ensure_ssh_key "dbaas-control-plane" "$SSH_KEY_PATH.pub")
    log_info "SSH Key ID: $SSH_KEY_ID"

    # Step 2: Get control plane IPs
    log_info "Step 2: Getting control plane IPs..."
    CP_INFO=$(get_cp_ips)
    echo "$CP_INFO"

    CP1_IP=$(echo "$CP_INFO" | grep "dbaas-cp-1" | cut -d: -f2)
    CP2_IP=$(echo "$CP_INFO" | grep "dbaas-cp-2" | cut -d: -f2)
    CP3_IP=$(echo "$CP_INFO" | grep "dbaas-cp-3" | cut -d: -f2)

    if [[ -z "$CP1_IP" || -z "$CP2_IP" || -z "$CP3_IP" ]]; then
        log_error "Could not find all 3 control plane IPs"
        log_error "Found: CP1=$CP1_IP, CP2=$CP2_IP, CP3=$CP3_IP"
        exit 1
    fi

    log_info "Control Plane IPs: $CP1_IP, $CP2_IP, $CP3_IP"

    # Step 3: Create monitoring VPS
    log_info "Step 3: Creating monitoring VPS from snapshot..."
    RESULT=$(hetzner_create_server_from_snapshot "$MONITORING_NAME" "$MONITORING_SERVER_TYPE" "$MONITORING_LOCATION" "$SSH_KEY_ID" "$CUSTOMER_SNAPSHOT_ID")
    MONITORING_ID=$(echo "$RESULT" | cut -d'|' -f1)
    MONITORING_IP=$(echo "$RESULT" | cut -d'|' -f2)
    log_info "Monitoring VPS: ID=$MONITORING_ID, IP=$MONITORING_IP"

    # Step 4: Wait for SSH
    log_info "Step 4: Waiting for SSH to be ready..."
    sleep 15
    local retries=30
    until ssh_cmd "$MONITORING_IP" "echo ready" 2>/dev/null; do
        retries=$((retries - 1))
        if [[ $retries -le 0 ]]; then
            log_error "Timeout waiting for SSH"
            exit 1
        fi
        sleep 5
    done
    log_info "SSH is ready"

    # Step 5: Create directories and prepare configs
    log_info "Step 5: Setting up monitoring directory structure..."
    ssh_cmd "$MONITORING_IP" "mkdir -p /opt/monitoring/alerts /opt/monitoring/provisioning/datasources /opt/monitoring/provisioning/dashboards /opt/monitoring/dashboards"

    # Step 6: Upload and configure files
    log_info "Step 6: Uploading configuration files..."

    # Create temporary directory for modified configs
    TEMP_DIR=$(mktemp -d)
    trap "rm -rf $TEMP_DIR" EXIT

    # Copy configs to temp and replace placeholders
    cp -r "$MONITORING_CONFIGS"/* "$TEMP_DIR/"

    # Replace CP IPs in prometheus.yml
    sed -i.bak "s/CP1_IP/$CP1_IP/g" "$TEMP_DIR/prometheus.yml"
    sed -i.bak "s/CP2_IP/$CP2_IP/g" "$TEMP_DIR/prometheus.yml"
    sed -i.bak "s/CP3_IP/$CP3_IP/g" "$TEMP_DIR/prometheus.yml"

    # Replace CP IPs in alertmanager.yml
    sed -i.bak "s/CP1_IP/$CP1_IP/g" "$TEMP_DIR/alertmanager.yml"

    # Replace CP IPs in docker-compose.yml
    sed -i.bak "s/CP1_IP/$MONITORING_IP/g" "$TEMP_DIR/docker-compose.yml"

    # Update Grafana password in docker-compose.yml
    sed -i.bak "s|dbaas-grafana-2024|$GRAFANA_PASSWORD|g" "$TEMP_DIR/docker-compose.yml"

    # Remove backup files
    find "$TEMP_DIR" -name "*.bak" -delete

    # Upload files
    log_info "  Uploading docker-compose.yml..."
    scp_file "$TEMP_DIR/docker-compose.yml" "$MONITORING_IP" "/opt/monitoring/"

    log_info "  Uploading prometheus.yml..."
    scp_file "$TEMP_DIR/prometheus.yml" "$MONITORING_IP" "/opt/monitoring/"

    log_info "  Uploading alertmanager.yml..."
    scp_file "$TEMP_DIR/alertmanager.yml" "$MONITORING_IP" "/opt/monitoring/"

    log_info "  Uploading alert rules..."
    scp_dir "$TEMP_DIR/alerts/" "$MONITORING_IP" "/opt/monitoring/"

    log_info "  Uploading Grafana provisioning..."
    scp_dir "$TEMP_DIR/provisioning/" "$MONITORING_IP" "/opt/monitoring/"

    log_info "  Uploading Grafana dashboards..."
    scp_dir "$TEMP_DIR/dashboards/" "$MONITORING_IP" "/opt/monitoring/"

    # Create API key file
    log_info "  Creating API key file..."
    ssh_cmd "$MONITORING_IP" "echo '$INTERNAL_API_KEY' > /opt/monitoring/api-key && chmod 644 /opt/monitoring/api-key"

    # Step 7: Start containers
    log_info "Step 7: Starting monitoring containers..."
    ssh_cmd "$MONITORING_IP" "cd /opt/monitoring && docker compose up -d"

    # Step 8: Wait for services to be healthy
    log_info "Step 8: Waiting for services to be healthy..."
    sleep 15

    local prometheus_health
    prometheus_health=$(ssh_cmd "$MONITORING_IP" "curl -s http://localhost:9090/-/healthy" 2>/dev/null || echo "error")
    if [[ "$prometheus_health" == "Prometheus Server is Healthy." ]]; then
        log_info "  Prometheus: healthy"
    else
        log_warn "  Prometheus: $prometheus_health"
    fi

    local grafana_health
    grafana_health=$(ssh_cmd "$MONITORING_IP" "curl -s http://localhost:3000/api/health" 2>/dev/null | jq -r '.database' || echo "error")
    if [[ "$grafana_health" == "ok" ]]; then
        log_info "  Grafana: healthy"
    else
        log_warn "  Grafana: $grafana_health"
    fi

    local alertmanager_health
    alertmanager_health=$(ssh_cmd "$MONITORING_IP" "curl -s http://localhost:9093/-/healthy" 2>/dev/null || echo "error")
    if [[ "$alertmanager_health" == "OK" ]]; then
        log_info "  Alertmanager: healthy"
    else
        log_warn "  Alertmanager: $alertmanager_health"
    fi

    # Summary
    echo ""
    echo -e "${BLUE}══════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}              Monitoring VPS Deployed Successfully!               ${NC}"
    echo -e "${BLUE}══════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${GREEN}Monitoring VPS:${NC}"
    echo "  IP: $MONITORING_IP"
    echo "  Server ID: $MONITORING_ID"
    echo ""
    echo -e "${GREEN}Services:${NC}"
    echo "  Prometheus:    http://$MONITORING_IP:9090"
    echo "  Grafana:       http://$MONITORING_IP:3000"
    echo "  Alertmanager:  http://$MONITORING_IP:9093"
    echo ""
    echo -e "${GREEN}Grafana Login:${NC}"
    echo "  Username: admin"
    echo "  Password: $GRAFANA_PASSWORD"
    echo ""
    echo -e "${GREEN}Control Plane Nodes Being Monitored:${NC}"
    echo "  CP-1: $CP1_IP"
    echo "  CP-2: $CP2_IP"
    echo "  CP-3: $CP3_IP"
    echo ""
    echo -e "${YELLOW}╔══════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${YELLOW}║  Add this to your API deployment (Step 5 in initial-setup.md):  ║${NC}"
    echo -e "${YELLOW}╠══════════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${YELLOW}║${NC}  PROMETHEUS_URL=http://$MONITORING_IP:9090                       ${YELLOW}║${NC}"
    echo -e "${YELLOW}╚══════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    log_info "Monitoring setup complete!"
}

main "$@"
