package com.example.scheduler.controller.dto;

import com.example.scheduler.controlplane.client.dto.LoadShapeRequest;
import lombok.Data;

import java.util.Map;

@Data
public class OperationExecutionRequest {
    private Map<String, Object> parameters;
    private LoadShapeRequest loadShape;
}
