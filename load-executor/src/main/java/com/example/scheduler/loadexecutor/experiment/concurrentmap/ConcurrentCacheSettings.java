package com.example.scheduler.loadexecutor.experiment.concurrentmap;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ConcurrentCacheSettings {
    String cacheName;
    int keySpaceSize;
    int hotKeyCount;
    double hotKeyRatio;
    int valueSizeBytes;
    int computeCostMicros;
    int maxEntries;
    int initialCapacity;
    double loadFactor;
    int concurrencyLevel;
    int ttlSeconds;
    boolean resetCache;

    public ConcurrentCacheSettings normalized() {
        return toBuilder()
                .keySpaceSize(Math.max(1, keySpaceSize))
                .hotKeyCount(Math.max(1, hotKeyCount))
                .hotKeyRatio(Math.max(0d, Math.min(0.99d, hotKeyRatio)))
                .valueSizeBytes(Math.max(1, valueSizeBytes))
                .computeCostMicros(Math.max(0, computeCostMicros))
                .maxEntries(Math.max(1, maxEntries))
                .initialCapacity(Math.max(1, initialCapacity))
                .loadFactor(Math.min(0.99d, Math.max(0.25d, loadFactor)))
                .concurrencyLevel(Math.max(1, concurrencyLevel))
                .ttlSeconds(Math.max(1, ttlSeconds))
                .build();
    }
}
