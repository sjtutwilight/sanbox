package com.example.scheduler.service.core;

import lombok.Builder;
import lombok.Getter;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Definition for write-through execution.
 */
@Getter
@Builder
public class WriteThroughPlan<T> {
    private final int batchSize;
    private final Supplier<T> payloadSupplier;
    private final Consumer<T> persistenceWriter;
    private final Consumer<T> cacheWriter;
}
