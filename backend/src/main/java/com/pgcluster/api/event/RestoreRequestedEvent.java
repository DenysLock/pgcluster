package com.pgcluster.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Event published when a restore job is created and committed to the database.
 * Used to trigger async restore execution after transaction commit.
 */
@Getter
public class RestoreRequestedEvent extends ApplicationEvent {

    private final UUID restoreJobId;
    private final boolean createNewCluster;

    public RestoreRequestedEvent(Object source, UUID restoreJobId, boolean createNewCluster) {
        super(source);
        this.restoreJobId = restoreJobId;
        this.createNewCluster = createNewCluster;
    }
}
