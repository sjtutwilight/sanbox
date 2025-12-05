package com.example.scheduler.loadexecutor.domain;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class RunMetrics {
    long totalRequests;
    long successfulRequests;
    long failedRequests;
    double avgLatencyMs;
    long maxLatencyMs;
    double currentQps;
    Instant lastUpdated;
}
