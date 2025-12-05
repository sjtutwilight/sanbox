package com.example.scheduler.loadexecutor.domain;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Duration;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class LoadShape {

    LoadShapeType type;
    int targetQps;
    Integer maxConcurrency;
    Duration duration;
    @Singular("param")
    Map<String, Object> params;

    public int resolveConcurrency(int defaultConcurrency) {
        return maxConcurrency != null && maxConcurrency > 0 ? maxConcurrency : defaultConcurrency;
    }
}
