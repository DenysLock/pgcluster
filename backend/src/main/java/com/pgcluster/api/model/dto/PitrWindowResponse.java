package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class PitrWindowResponse {

    private boolean available;
    private Instant earliestPitrTime;
    private Instant latestPitrTime;
}
