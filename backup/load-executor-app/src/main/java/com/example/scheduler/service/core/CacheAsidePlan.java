package com.example.scheduler.service.core;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Definition for a cache-aside execution plan.
 */
@Getter
@Builder
public class CacheAsidePlan<T> {
    private final Supplier<List<T>> cacheReader;
    private final Supplier<List<T>> sourceLoader;
    private final Consumer<List<T>> cacheWriter;
    private final Runnable emptyValueWriter;
}
