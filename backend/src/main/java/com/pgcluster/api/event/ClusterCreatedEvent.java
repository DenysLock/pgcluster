package com.pgcluster.api.event;

import com.pgcluster.api.model.entity.Cluster;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a new cluster is created and committed to the database.
 * Used to trigger async provisioning after transaction commit.
 */
@Getter
public class ClusterCreatedEvent extends ApplicationEvent {

    private final Cluster cluster;

    public ClusterCreatedEvent(Object source, Cluster cluster) {
        super(source);
        this.cluster = cluster;
    }
}
