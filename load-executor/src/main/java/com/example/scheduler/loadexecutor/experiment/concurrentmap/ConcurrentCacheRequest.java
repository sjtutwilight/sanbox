package com.example.scheduler.loadexecutor.experiment.concurrentmap;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import com.example.scheduler.loadexecutor.experiment.support.RequestSupport;
import lombok.Value;

import java.time.Duration;
import java.util.Map;

@Value
public class ConcurrentCacheRequest {
    ConcurrentCacheSettings settings;
    Duration ttl;
    int invalidateRange;
    int refreshBatch;
    String keyPrefix;

    public static ConcurrentCacheRequest from(OperationInvocationContext context,
                                              ConcurrentCacheSettings defaultSettings,
                                              String keyPrefix,
                                              int defaultInvalidateRange,
                                              int defaultRefreshBatch) {
        Map<String, Object> payload = context.getPayload();
        ConcurrentCacheSettings settings = buildSettings(payload, defaultSettings, keyPrefix);
        Duration ttl = RequestSupport.durationValue(payload != null ? payload.get("ttlSeconds") : null,
                Duration.ofSeconds(defaultSettings.getTtlSeconds()));
        int invalidateRange = RequestSupport.intValue(payload != null ? payload.get("invalidateRange") : null, defaultInvalidateRange);
        int refreshBatch = RequestSupport.intValue(payload != null ? payload.get("refreshBatch") : null, defaultRefreshBatch);
        return new ConcurrentCacheRequest(settings.normalized(), ttl, Math.max(1, invalidateRange), Math.max(1, refreshBatch), keyPrefix);
    }

    private static ConcurrentCacheSettings buildSettings(Map<String, Object> payload,
                                                         ConcurrentCacheSettings defaults,
                                                         String cacheName) {
        if (payload == null) {
            return defaults;
        }
        return defaults.toBuilder()
                .cacheName(cacheName)
                .keySpaceSize(RequestSupport.intValue(payload.get("keySpaceSize"), defaults.getKeySpaceSize()))
                .hotKeyCount(RequestSupport.intValue(payload.get("hotKeyCount"), defaults.getHotKeyCount()))
                .hotKeyRatio(RequestSupport.doubleValue(payload.get("hotKeyRatio"), defaults.getHotKeyRatio()))
                .valueSizeBytes(RequestSupport.intValue(payload.get("valueSizeBytes"), defaults.getValueSizeBytes()))
                .computeCostMicros(RequestSupport.intValue(payload.get("computeCostMicros"), defaults.getComputeCostMicros()))
                .maxEntries(RequestSupport.intValue(payload.get("maxEntries"), defaults.getMaxEntries()))
                .initialCapacity(RequestSupport.intValue(payload.get("initialCapacity"), defaults.getInitialCapacity()))
                .loadFactor(RequestSupport.doubleValue(payload.get("loadFactor"), defaults.getLoadFactor()))
                .concurrencyLevel(RequestSupport.intValue(payload.get("concurrencyLevel"), defaults.getConcurrencyLevel()))
                .ttlSeconds(RequestSupport.intValue(payload.get("ttlSeconds"), defaults.getTtlSeconds()))
                .resetCache(RequestSupport.boolValue(payload.get("resetCache"), defaults.isResetCache()))
                .build();
    }
}
