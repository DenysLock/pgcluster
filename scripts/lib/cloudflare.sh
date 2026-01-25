#!/bin/bash
#
# Cloudflare API functions
#

CLOUDFLARE_API="https://api.cloudflare.com/client/v4"

# Create DNS A record
# Usage: cloudflare_create_record <name> <ip> [proxied] [ttl]
cloudflare_create_record() {
    local name="$1"
    local ip="$2"
    local proxied="${3:-false}"
    local ttl="${4:-1}"  # 1 = auto, or specify seconds (60, 120, etc.)

    # Check if record exists
    local existing
    existing=$(curl -s "${CLOUDFLARE_API}/zones/${CLOUDFLARE_ZONE_ID}/dns_records?name=${name}.${DOMAIN}" \
        -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" | \
        jq -r '.result[0].id')

    local result
    if [[ -n "$existing" && "$existing" != "null" ]]; then
        # Update existing record
        result=$(curl -s -X PUT "${CLOUDFLARE_API}/zones/${CLOUDFLARE_ZONE_ID}/dns_records/${existing}" \
            -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
            -H "Content-Type: application/json" \
            -d "{
                \"type\": \"A\",
                \"name\": \"$name\",
                \"content\": \"$ip\",
                \"proxied\": $proxied,
                \"ttl\": $ttl
            }")
    else
        # Create new record
        result=$(curl -s -X POST "${CLOUDFLARE_API}/zones/${CLOUDFLARE_ZONE_ID}/dns_records" \
            -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
            -H "Content-Type: application/json" \
            -d "{
                \"type\": \"A\",
                \"name\": \"$name\",
                \"content\": \"$ip\",
                \"proxied\": $proxied,
                \"ttl\": $ttl
            }")
    fi

    # Validate response
    if ! echo "$result" | jq -e '.success == true' > /dev/null 2>&1; then
        local error_msg
        error_msg=$(echo "$result" | jq -r '.errors[0].message // "Unknown error"')
        log_error "Cloudflare API failed for ${name}: ${error_msg}" >&2
        return 1
    fi
}

# Delete DNS record
cloudflare_delete_record() {
    local name="$1"

    local record_id
    record_id=$(curl -s "${CLOUDFLARE_API}/zones/${CLOUDFLARE_ZONE_ID}/dns_records?name=${name}.${DOMAIN}" \
        -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" | \
        jq -r '.result[0].id')

    if [[ -n "$record_id" && "$record_id" != "null" ]]; then
        curl -s -X DELETE "${CLOUDFLARE_API}/zones/${CLOUDFLARE_ZONE_ID}/dns_records/${record_id}" \
            -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" > /dev/null
    fi
}
