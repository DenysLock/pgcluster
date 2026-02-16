package com.pgcluster.api.exception;

import lombok.Getter;

import java.time.Instant;

@Getter
public class PitrValidationException extends RuntimeException {

    public static final String CODE_TARGET_BEFORE_EARLIEST = "PITR_TARGET_BEFORE_EARLIEST";
    public static final String CODE_TARGET_AFTER_LATEST = "PITR_TARGET_AFTER_LATEST";
    public static final String CODE_TARGET_IN_GAP = "PITR_TARGET_IN_GAP";
    public static final String CODE_TARGET_NOT_RECOVERABLE = "PITR_TARGET_NOT_RECOVERABLE";

    private final String code;
    private final Instant requestedTargetTime;
    private final Instant nearestBefore;
    private final Instant nearestAfter;
    private final Instant earliestPitrTime;
    private final Instant latestPitrTime;

    public PitrValidationException(
            String message,
            String code,
            Instant requestedTargetTime,
            Instant nearestBefore,
            Instant nearestAfter,
            Instant earliestPitrTime,
            Instant latestPitrTime
    ) {
        super(message);
        this.code = code;
        this.requestedTargetTime = requestedTargetTime;
        this.nearestBefore = nearestBefore;
        this.nearestAfter = nearestAfter;
        this.earliestPitrTime = earliestPitrTime;
        this.latestPitrTime = latestPitrTime;
    }
}
