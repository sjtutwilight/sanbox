package com.example.scheduler.controlplane.client.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Wire response for a single experiment run.
 */
@Data
public class ExperimentRunResponse {
    private String experimentRunId;
    private String experimentId;
    private String groupId;
    private String operationId;
    private RunStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
    private String lastError;
    private Map<String, Object> command;
    private RunMetricsResponse metrics;
}
