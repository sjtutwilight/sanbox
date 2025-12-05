package com.example.scheduler.controlplane.client.dto;

import com.example.scheduler.experiment.OperationType;
import lombok.Data;

import java.util.List;

/**
 * Operation metadata entry.
 */
@Data
public class ExperimentOperationResponse {
    private String operationId;
    private String label;
    private String description;
    private String hint;
    private OperationType operationType;
    private List<OperationParameterResponse> parameters;
    private LoadShapeTemplateResponse loadShapeTemplate;
}
