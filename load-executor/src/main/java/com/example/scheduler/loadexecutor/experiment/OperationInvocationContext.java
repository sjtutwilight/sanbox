package com.example.scheduler.loadexecutor.experiment;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.LoadPhase;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class OperationInvocationContext {
    Command command;
    LoadPhase phase;
    Map<String, Object> payload;
    long sequence;
    Instant scheduledAt;
    Instant startedAt;
}
