package com.example.scheduler.loadexecutor.api;

import com.example.scheduler.loadexecutor.domain.RunMetrics;
import com.example.scheduler.loadexecutor.domain.RunStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class ExperimentRunResponse {
    String experimentRunId;
    String experimentId;
    String groupId;
    String operationId;
    RunStatus status;
    Instant createdAt;
    Instant startedAt;
    Instant endedAt;
    String lastError;
    Map<String, Object> command;
    RunMetrics metrics;
}
