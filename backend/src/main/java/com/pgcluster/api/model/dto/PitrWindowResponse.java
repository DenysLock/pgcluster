package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitrWindowResponse {

    private boolean available;
    private Instant earliestPitrTime;
    private Instant latestPitrTime;
    @Builder.Default
    private List<PitrInterval> intervals = List.of();
    private String status;
    private String unavailableReason;
    private Instant asOf;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PitrInterval {
        private Instant startTime;
        private Instant endTime;
    }
}
