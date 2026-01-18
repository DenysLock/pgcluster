package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response DTO for cluster credentials - only returned via dedicated secure endpoint.
 * These credentials are sensitive and should not be logged or cached.
 */
@Data
@Builder
@AllArgsConstructor
public class ClusterCredentialsResponse {

    private String hostname;
    private int port;
    private String database;
    private String username;
    private String password;
    private String connectionString;
    private String sslMode;
    private Instant retrievedAt;
    private String warning;
}
