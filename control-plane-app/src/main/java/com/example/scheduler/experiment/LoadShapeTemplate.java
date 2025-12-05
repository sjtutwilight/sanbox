package com.example.scheduler.experiment;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class LoadShapeTemplate {
    private String type;
    private Integer qps;
    private Integer concurrency;
    private Long durationSeconds;
    private Map<String, Object> params;
}
