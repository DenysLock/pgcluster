package com.pgcluster.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Event published when a new backup is created and committed to the database.
 * Used to trigger async backup execution after transaction commit.
 */
@Getter
public class BackupCreatedEvent extends ApplicationEvent {

    private final UUID backupId;

    public BackupCreatedEvent(Object source, UUID backupId) {
        super(source);
        this.backupId = backupId;
    }
}
