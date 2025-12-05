package com.example.scheduler.loadexecutor.experiment;

import com.example.scheduler.experiment.OperationType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExperimentOperationHandle {
    String experimentId;
    String groupId;
    String operationId;
    OperationType operationType;
    ExperimentOperationInvoker invoker;
    ExperimentDescriptor descriptor;
}
