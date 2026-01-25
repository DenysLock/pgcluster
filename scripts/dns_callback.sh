#!/bin/bash
# DNS Callback Script for Control Plane Patroni
#
# This script is called by Patroni when a role change occurs.
# It updates the Cloudflare DNS record to point to the new leader.
#
# Usage by Patroni:
#   dns_callback.sh <action> <role> <cluster-name>
#
# Example:
#   dns_callback.sh on_role_change master control-plane
#
# Required environment variables (set in /etc/patroni/dns_callback.env):
#   CP_NODE_IP          - This node's public IP address
#   CLOUDFLARE_API_TOKEN - Cloudflare API token with DNS edit permissions
#   CLOUDFLARE_ZONE_ID   - Cloudflare zone ID
#   DNS_RECORD_ID        - Record ID for api.pgcluster.com
#
# Installation:
#   1. Copy this script to /etc/patroni/dns_callback.sh
#   2. chmod +x /etc/patroni/dns_callback.sh
#   3. Create /etc/patroni/dns_callback.env with the variables above
#   4. Add to patroni.yml:
#      postgresql:
#        callbacks:
#          on_role_change: /etc/patroni/dns_callback.sh

set -euo pipefail

# Load environment variables
if [ -f /etc/patroni/dns_callback.env ]; then
    source /etc/patroni/dns_callback.env
fi

# Arguments from Patroni
ACTION="${1:-}"
ROLE="${2:-}"
CLUSTER_NAME="${3:-}"

log() {
    logger -t "dns_callback" "$1"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Only update DNS when becoming the leader
if [ "$ROLE" == "master" ] || [ "$ROLE" == "primary" ]; then
    log "Role changed to $ROLE for cluster $CLUSTER_NAME"

    # Validate required variables
    if [ -z "${CP_NODE_IP:-}" ]; then
        log "ERROR: CP_NODE_IP not set"
        exit 1
    fi
    if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
        log "ERROR: CLOUDFLARE_API_TOKEN not set"
        exit 1
    fi
    if [ -z "${CLOUDFLARE_ZONE_ID:-}" ]; then
        log "ERROR: CLOUDFLARE_ZONE_ID not set"
        exit 1
    fi
    if [ -z "${DNS_RECORD_ID:-}" ]; then
        log "ERROR: DNS_RECORD_ID not set"
        exit 1
    fi

    log "Updating DNS record to point to ${CP_NODE_IP}"

    RESPONSE=$(curl -s -X PATCH \
        "https://api.cloudflare.com/client/v4/zones/${CLOUDFLARE_ZONE_ID}/dns_records/${DNS_RECORD_ID}" \
        -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"content\": \"${CP_NODE_IP}\"}")

    # Check if successful
    if echo "$RESPONSE" | grep -q '"success":true'; then
        log "SUCCESS: DNS updated to ${CP_NODE_IP}"
    else
        log "ERROR: Failed to update DNS: $RESPONSE"
        exit 1
    fi
else
    log "Role is $ROLE (not leader), no DNS update needed"
fi
