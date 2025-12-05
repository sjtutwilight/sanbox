package com.example.scheduler.loadexecutor.experiment;

import com.example.scheduler.experiment.OperationType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopExperimentGroup implements ExperimentGroup {
    @Override
    public String experimentId() {
        return "noop";
    }

    @Override
    public String groupId() {
        return "default";
    }

    @Override
    public List<ExperimentOperationDefinition> operations() {
        return List.of(ExperimentOperationDefinition.builder()
                .operationId("ping")
                .description("No-op experiment operation for smoke tests")
                .operationType(OperationType.CONTINUOUS_READ)
                .invoker(ctx -> null)
                .build());
    }
}
