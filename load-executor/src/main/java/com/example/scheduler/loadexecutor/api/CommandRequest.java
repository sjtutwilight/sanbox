package com.example.scheduler.loadexecutor.api;

import com.example.scheduler.experiment.OperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CommandRequest {

    private String commandId;

    @NotBlank
    private String experimentId;

    private String groupId;

    @NotBlank
    private String operationId;

    private String experimentRunId;
    
    @NotBlank
    private String platform;

    @NotBlank
    private String scenario;

    private OperationType operationType;

    private Map<String, Object> dataRequest;

    private Map<String, Object> overrides;

    @Valid
    @NotNull
    private LoadShapeRequest loadShape;
}
