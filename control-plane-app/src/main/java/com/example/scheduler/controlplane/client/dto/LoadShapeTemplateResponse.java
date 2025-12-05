package com.example.scheduler.controlplane.client.dto;

import lombok.Data;

import java.util.Map;

/**
 * Load shape template definition for an operation.
 */
@Data
public class LoadShapeTemplateResponse {
    private String type;
    private Integer qps;
    private Integer concurrency;
    private Long durationSeconds;
    private Map<String, Object> params;
}
