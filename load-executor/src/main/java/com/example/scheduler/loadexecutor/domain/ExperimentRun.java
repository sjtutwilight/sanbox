package com.example.scheduler.loadexecutor.domain;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class ExperimentRun {
    String id;
    Command command;
    RunStatus status;
    Instant createdAt;
    Instant startedAt;
    Instant endedAt;
    String lastError;
    RunMetrics metrics;

    public ExperimentRun markStatus(RunStatus status) {
        return toBuilder().status(status).build();
    }

    public ExperimentRun markStarted(Instant startedAt) {
        return toBuilder().status(RunStatus.RUNNING).startedAt(startedAt).build();
    }

    public ExperimentRun markEnded(RunStatus status, Instant endedAt, String error) {
        return toBuilder()
                .status(status)
                .endedAt(endedAt)
                .lastError(error)
                .build();
    }

    public ExperimentRun withMetrics(RunMetrics metrics) {
        return toBuilder().metrics(metrics).build();
    }
}
