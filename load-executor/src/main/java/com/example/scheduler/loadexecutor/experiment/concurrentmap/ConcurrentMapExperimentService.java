package com.example.scheduler.loadexecutor.experiment.concurrentmap;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ConcurrentMapExperimentService {

    private final MeterRegistry meterRegistry;
    private final Map<String, ConcurrentHotCacheEngine> caches = new ConcurrentHashMap<>();

    public ConcurrentHotCacheEngine.CacheReadResult read(String cacheName, ConcurrentCacheRequest request) {
        return engine(cacheName, request.getSettings()).read(request);
    }

    public ConcurrentHotCacheEngine.CacheInvalidateResult invalidate(String cacheName, ConcurrentCacheRequest request) {
        return engine(cacheName, request.getSettings()).invalidate(request);
    }

    public ConcurrentHotCacheEngine.CacheRefreshResult refresh(String cacheName, ConcurrentCacheRequest request) {
        return engine(cacheName, request.getSettings()).refresh(request);
    }

    private ConcurrentHotCacheEngine engine(String cacheName, ConcurrentCacheSettings settings) {
        return caches.computeIfAbsent(cacheName,
                key -> new ConcurrentHotCacheEngine(key, settings, meterRegistry));
    }
}
