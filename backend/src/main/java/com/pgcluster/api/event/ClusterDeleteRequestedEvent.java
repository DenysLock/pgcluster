package com.pgcluster.api.event;

import com.pgcluster.api.model.entity.Cluster;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a cluster deletion is requested and status is committed.
 * Used to trigger async deletion after transaction commit.
 */
@Getter
public class ClusterDeleteRequestedEvent extends ApplicationEvent {

    private final Cluster cluster;

    public ClusterDeleteRequestedEvent(Object source, Cluster cluster) {
        super(source);
        this.cluster = cluster;
    }
}
