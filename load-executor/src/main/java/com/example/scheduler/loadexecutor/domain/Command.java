package com.example.scheduler.loadexecutor.domain;

import com.example.scheduler.experiment.OperationType;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Domain representation of a command pushed from the control plane.
 */
@Value
@Builder(toBuilder = true)
public class Command {

    String commandId;
    String experimentId;
    String groupId;
    String operationId;
    String experimentRunId;
    String platform;
    String scenario;
    OperationType operationType;

    /**
     * Raw payload from control plane, passed to experiment invoker as-is after decoration.
     */
    @Singular("dataEntry")
    Map<String, Object> dataRequest;

    /**
     * Optional runtime overrides, e.g. to tweak experiment-level configs.
     */
    @Singular("override")
    Map<String, Object> overrides;

    LoadShape loadShape;

    @Builder.Default
    Instant submittedAt = Instant.now();
}
