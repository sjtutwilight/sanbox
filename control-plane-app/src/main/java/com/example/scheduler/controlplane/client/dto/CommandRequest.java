package com.example.scheduler.controlplane.client.dto;

import com.example.scheduler.experiment.OperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request payload for load-executor /commands endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandRequest {

    private String commandId;
    private String experimentId;
    private String groupId;
    private String operationId;
    private String experimentRunId;
    private OperationType operationType;
    private Map<String, Object> dataRequest;
    private Map<String, Object> overrides;
    private LoadShapeRequest loadShape;
}
