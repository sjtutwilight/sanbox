package com.example.scheduler.controlplane.client.dto;

import lombok.Data;

import java.time.Instant;

/**
 * Metrics section returned for a run.
 */
@Data
public class RunMetricsResponse {
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private double avgLatencyMs;
    private long maxLatencyMs;
    private double currentQps;
    private Instant lastUpdated;
}
