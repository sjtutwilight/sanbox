package com.example.scheduler.loadexecutor.experiment;

import com.example.scheduler.experiment.OperationType;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class ExperimentOperationDefinition {
    String operationId;
    String label;
    String hint;
    String description;
    OperationType operationType;
    ExperimentOperationInvoker invoker;
    @Singular("parameter")
    List<OperationParameter> parameters;
    LoadShapeTemplate loadShapeTemplate;

    public List<OperationParameter> getParameters() {
        return parameters == null ? Collections.emptyList() : parameters;
    }
}
