package com.pgcluster.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Event published when a new export is created and committed to the database.
 * Used to trigger async export execution after transaction commit.
 */
@Getter
public class ExportCreatedEvent extends ApplicationEvent {

    private final UUID exportId;

    public ExportCreatedEvent(Object source, UUID exportId) {
        super(source);
        this.exportId = exportId;
    }
}
