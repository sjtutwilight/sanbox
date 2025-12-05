package com.example.scheduler.controlplane.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Sub-payload carrying load shape hints for the executor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadShapeRequest {
    private String type;
    private int qps;
    private Integer concurrency;
    private Long durationSeconds;
    private Map<String, Object> params;
}
