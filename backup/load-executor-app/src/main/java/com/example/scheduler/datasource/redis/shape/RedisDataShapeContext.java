package com.example.scheduler.datasource.redis.shape;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.client.RedisStructureClient;
import lombok.Builder;
import lombok.Getter;

/**
 * Context passed to concrete Redis data shapes for batch writes.
 */
@Getter
@Builder
public class RedisDataShapeContext {
    private final RedisStructureClient redis;
    private final DataGenerationRequest request;
    private final RequestDefaults defaults;
    private final long startIndex;
    private final int count;
}
