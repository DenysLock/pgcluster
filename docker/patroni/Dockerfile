FROM postgres:16-bookworm

# Install Patroni and dependencies
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-psycopg2 \
    python3-yaml \
    python3-requests \
    curl \
    jq \
    iproute2 \
    && pip3 install --break-system-packages \
        patroni[etcd3]==4.0.4 \
        python-etcd \
    && rm -rf /var/lib/apt/lists/*

# Create patroni user and directories
RUN mkdir -p /etc/patroni /var/lib/postgresql/data \
    && chown -R postgres:postgres /etc/patroni /var/lib/postgresql

# Patroni entrypoint
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

USER postgres
EXPOSE 5432 8008

ENTRYPOINT ["/entrypoint.sh"]
CMD ["patroni", "/etc/patroni/patroni.yml"]
