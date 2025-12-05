package com.example.scheduler.loadexecutor.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class LoadShapeRequest {

    @NotBlank
    private String type;

    @Min(1)
    private int qps;

    @Min(1)
    private Integer concurrency;

    private Long durationSeconds;

    private Map<String, Object> params;
}
