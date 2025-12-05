package com.example.scheduler.loadexecutor.experiment;

import com.example.scheduler.loadexecutor.domain.LoadShapeType;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class LoadShapeTemplate {
    LoadShapeType type;
    Integer qps;
    Integer concurrency;
    Long durationSeconds;
    Map<String, Object> params;
}
