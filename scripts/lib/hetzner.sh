#!/bin/bash
#
# Hetzner Cloud API functions
#

HETZNER_API="https://api.hetzner.cloud/v1"

# Ensure SSH key exists in Hetzner (replaces if different), return key ID
hetzner_ensure_ssh_key() {
    local name="$1"
    local public_key_path="$2"

    local public_key
    public_key=$(cat "$public_key_path")
    # Hetzner uses MD5 fingerprint format (colon-separated hex)
    local local_fingerprint
    local_fingerprint=$(ssh-keygen -E md5 -lf "$public_key_path" 2>/dev/null | awk '{print $2}' | sed 's/^MD5://')

    # First, check if this fingerprint already exists (under any name)
    local all_keys
    all_keys=$(curl -s "${HETZNER_API}/ssh_keys" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN")

    local existing_by_fingerprint
    existing_by_fingerprint=$(echo "$all_keys" | \
        jq -r ".ssh_keys[] | select(.fingerprint==\"$local_fingerprint\")")

    if [[ -n "$existing_by_fingerprint" ]]; then
        # Key with this fingerprint exists - return its ID
        local existing_id
        existing_id=$(echo "$existing_by_fingerprint" | jq -r '.id')
        local existing_name
        existing_name=$(echo "$existing_by_fingerprint" | jq -r '.name')
        if [[ "$existing_name" != "$name" ]]; then
            log_info "SSH key exists as '$existing_name' (fingerprint match), using it" >&2
        fi
        echo "$existing_id"
        return
    fi

    # Check if a key with this name exists (but different fingerprint)
    local existing_by_name
    existing_by_name=$(echo "$all_keys" | \
        jq -r ".ssh_keys[] | select(.name==\"$name\")")

    if [[ -n "$existing_by_name" ]]; then
        # Name exists but fingerprint differs - delete old key
        local existing_id
        existing_id=$(echo "$existing_by_name" | jq -r '.id')
        log_warn "SSH key '$name' exists with different fingerprint, replacing..." >&2
        curl -s -X DELETE "${HETZNER_API}/ssh_keys/${existing_id}" \
            -H "Authorization: Bearer $HETZNER_API_TOKEN" > /dev/null
    fi

    # Create new key
    local result
    result=$(curl -s -X POST "${HETZNER_API}/ssh_keys" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"name\": \"$name\", \"public_key\": \"$public_key\"}")

    echo "$result" | jq -r '.ssh_key.id'
}

# Create a server, return "id|ip"
hetzner_create_server() {
    local name="$1"
    local server_type="$2"
    local location="$3"
    local ssh_key_id="$4"

    # Debug: check token is set
    if [[ -z "$HETZNER_API_TOKEN" ]]; then
        log_error "HETZNER_API_TOKEN is empty in hetzner_create_server!" >&2
        return 1
    fi

    # Check if server exists
    local api_response
    api_response=$(curl -s "${HETZNER_API}/servers?name=$name" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN")

    # Debug: log raw response if it looks like an error
    if echo "$api_response" | jq -e '.error' >/dev/null 2>&1; then
        log_error "API error: $(echo "$api_response" | jq -r '.error.message')" >&2
        return 1
    fi

    local existing
    existing=$(echo "$api_response" | jq -r '.servers[0] | "\(.id)|\(.public_net.ipv4.ip)"')

    if [[ "$existing" != "null|null" && -n "$existing" ]]; then
        echo "$existing"
        return
    fi

    # Create server
    local result
    result=$(curl -s -X POST "${HETZNER_API}/servers" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"name\": \"$name\",
            \"server_type\": \"$server_type\",
            \"image\": \"ubuntu-24.04\",
            \"location\": \"$location\",
            \"ssh_keys\": [\"$ssh_key_id\"],
            \"start_after_create\": true
        }")

    # Check for API errors
    if echo "$result" | jq -e '.error' >/dev/null 2>&1; then
        log_error "Failed to create server: $(echo "$result" | jq -r '.error.message')" >&2
        return 1
    fi

    local id
    id=$(echo "$result" | jq -r '.server.id')
    local ip
    ip=$(echo "$result" | jq -r '.server.public_net.ipv4.ip')

    # Validate server ID
    if [[ -z "$id" || "$id" == "null" ]]; then
        log_error "Server creation returned invalid ID. Response: $result" >&2
        return 1
    fi

    # Wait for IP to be assigned (with timeout)
    local retries=30
    while [[ -z "$ip" || "$ip" == "null" ]]; do
        retries=$((retries - 1))
        if [[ $retries -le 0 ]]; then
            log_error "Timeout waiting for IP assignment for server $id" >&2
            return 1
        fi
        sleep 2
        ip=$(curl -s "${HETZNER_API}/servers/$id" \
            -H "Authorization: Bearer $HETZNER_API_TOKEN" | \
            jq -r '.server.public_net.ipv4.ip')
    done

    echo "${id}|${ip}"
}

# Create floating IP (or return existing), return "id|ip"
hetzner_create_floating_ip() {
    local location="$1"
    local description="$2"

    # Check if FIP with same description already exists
    local existing
    existing=$(curl -s "${HETZNER_API}/floating_ips" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" | \
        jq -r ".floating_ips[] | select(.description==\"$description\") | \"\(.id)|\(.ip)\"")

    if [[ -n "$existing" ]]; then
        echo "$existing"
        return
    fi

    # Create new FIP
    local result
    result=$(curl -s -X POST "${HETZNER_API}/floating_ips" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"type\": \"ipv4\",
            \"home_location\": \"$location\",
            \"description\": \"$description\"
        }")

    local id
    id=$(echo "$result" | jq -r '.floating_ip.id')
    local ip
    ip=$(echo "$result" | jq -r '.floating_ip.ip')

    echo "${id}|${ip}"
}

# Assign floating IP to server
hetzner_assign_floating_ip() {
    local fip_id="$1"
    local server_id="$2"

    curl -s -X POST "${HETZNER_API}/floating_ips/${fip_id}/actions/assign" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"server\": $server_id}" > /dev/null
}

# Create a server from snapshot, return "id|ip"
hetzner_create_server_from_snapshot() {
    local name="$1"
    local server_type="$2"
    local location="$3"
    local ssh_key_id="$4"
    local snapshot_id="$5"

    # Debug: check token is set
    if [[ -z "$HETZNER_API_TOKEN" ]]; then
        log_error "HETZNER_API_TOKEN is empty!" >&2
        return 1
    fi

    if [[ -z "$snapshot_id" ]]; then
        log_error "SNAPSHOT_ID is required!" >&2
        return 1
    fi

    # Check if server exists
    local api_response
    api_response=$(curl -s "${HETZNER_API}/servers?name=$name" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN")

    if echo "$api_response" | jq -e '.error' >/dev/null 2>&1; then
        log_error "API error: $(echo "$api_response" | jq -r '.error.message')" >&2
        return 1
    fi

    local existing
    existing=$(echo "$api_response" | jq -r '.servers[0] | "\(.id)|\(.public_net.ipv4.ip)"')

    if [[ "$existing" != "null|null" && -n "$existing" ]]; then
        echo "$existing"
        return
    fi

    # Create server from snapshot
    log_info "API request: name=$name type=$server_type location=$location image=$snapshot_id ssh_key=$ssh_key_id" >&2
    local result
    result=$(curl -s -X POST "${HETZNER_API}/servers" \
        -H "Authorization: Bearer $HETZNER_API_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"name\": \"$name\",
            \"server_type\": \"$server_type\",
            \"image\": \"$snapshot_id\",
            \"location\": \"$location\",
            \"ssh_keys\": [\"$ssh_key_id\"],
            \"start_after_create\": true
        }")

    # Check for API errors
    if echo "$result" | jq -e '.error' >/dev/null 2>&1; then
        log_error "Failed to create server: $(echo "$result" | jq -r '.error.message')" >&2
        log_error "Full response: $result" >&2
        return 1
    fi

    local id
    id=$(echo "$result" | jq -r '.server.id')
    local ip
    ip=$(echo "$result" | jq -r '.server.public_net.ipv4.ip')

    # Validate server ID
    if [[ -z "$id" || "$id" == "null" ]]; then
        log_error "Server creation returned invalid ID. Response: $result" >&2
        return 1
    fi

    # Wait for IP to be assigned (with timeout)
    local retries=30
    while [[ -z "$ip" || "$ip" == "null" ]]; do
        retries=$((retries - 1))
        if [[ $retries -le 0 ]]; then
            log_error "Timeout waiting for IP assignment for server $id" >&2
            return 1
        fi
        sleep 2
        ip=$(curl -s "${HETZNER_API}/servers/$id" \
            -H "Authorization: Bearer $HETZNER_API_TOKEN" | \
            jq -r '.server.public_net.ipv4.ip')
    done

    echo "${id}|${ip}"
}
