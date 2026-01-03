#!/bin/bash
set -e

# If patroni.yml doesn't exist, wait for it (SSH upload might be in progress)
MAX_WAIT=60
WAITED=0

while [ ! -f /etc/patroni/patroni.yml ]; do
    echo "Waiting for /etc/patroni/patroni.yml... ($WAITED/$MAX_WAIT sec)"
    sleep 2
    WAITED=$((WAITED + 2))

    if [ $WAITED -ge $MAX_WAIT ]; then
        echo "ERROR: /etc/patroni/patroni.yml not found after $MAX_WAIT seconds"
        exit 1
    fi
done

echo "Configuration found, starting Patroni..."
exec "$@"
