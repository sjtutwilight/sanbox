package com.example.scheduler.datasource.redis.shape;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.client.RedisStructureClient;
import lombok.Builder;
import lombok.Getter;

/**
 * Context for shapes that support append-only continuous writes.
 */
@Getter
@Builder
public class RedisDataShapeAppendContext {
    private final RedisStructureClient redis;
    private final DataGenerationRequest request;
    private final RequestDefaults defaults;
    private final int batchSize;
}
