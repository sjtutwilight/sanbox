package com.example.scheduler.loadexecutor.experiment.concurrentmap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class ConcurrentHotCacheEngine {

    private final String cacheName;
    private final MeterRegistry meterRegistry;
    private final AtomicReference<ConcurrentHashMap<String, CacheEntry>> storeRef;
    private final AtomicReference<ConcurrentCacheSettings> settingsRef;

    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter invalidateCounter;
    private final Counter refreshCounter;
    private final Counter evictionCounter;
    private final Counter rebuildCounter;
    private final Timer computeTimer;

    public ConcurrentHotCacheEngine(String cacheName, ConcurrentCacheSettings defaultSettings, MeterRegistry meterRegistry) {
        this.cacheName = cacheName;
        this.meterRegistry = meterRegistry;
        ConcurrentCacheSettings normalized = defaultSettings.normalized();
        this.storeRef = new AtomicReference<>(new ConcurrentHashMap<>(normalized.getInitialCapacity(),
                (float) normalized.getLoadFactor(), normalized.getConcurrencyLevel()));
        this.settingsRef = new AtomicReference<>(normalized);
        this.computeTimer = Timer.builder("concurrent_cache_compute_seconds")
                .description("Duration to rebuild cache value")
                .tag("cache", cacheName)
                .register(meterRegistry);
        this.hitCounter = Counter.builder("concurrent_cache_hit_total").description("Cache hits").tag("cache", cacheName).register(meterRegistry);
        this.missCounter = Counter.builder("concurrent_cache_miss_total").description("Cache misses").tag("cache", cacheName).register(meterRegistry);
        this.invalidateCounter = Counter.builder("concurrent_cache_invalidate_total").description("Cache invalidations executed").tag("cache", cacheName).register(meterRegistry);
        this.refreshCounter = Counter.builder("concurrent_cache_refresh_total").description("Cache refresh batches executed").tag("cache", cacheName).register(meterRegistry);
        this.evictionCounter = Counter.builder("concurrent_cache_evict_total").description("Cache evictions due to maxEntries").tag("cache", cacheName).register(meterRegistry);
        this.rebuildCounter = Counter.builder("concurrent_cache_rebuild_total").description("Cache rebuild executions").tag("cache", cacheName).register(meterRegistry);
        registerMeters();
    }

    public CacheReadResult read(ConcurrentCacheRequest request) {
        applySettings(request.getSettings());
        ConcurrentHashMap<String, CacheEntry> map = storeRef.get();
        ConcurrentCacheSettings settings = settingsRef.get();
        String key = selectKey(settings, request.getKeyPrefix());
        long now = System.nanoTime();
        CacheEntry cached = map.get(key);
        if (cached != null && !cached.isExpired(now)) {
            hitCounter.increment();
            return new CacheReadResult(key, CacheReadResult.Source.HIT, map.size(), 0);
        }
        // computeIfAbsent intentionally keeps contention to mirror real cache hotspot behavior
        long start = System.nanoTime();
        CacheEntry computed = map.compute(key, (k, existing) -> {
            if (existing != null && !existing.isExpired(System.nanoTime())) {
                return existing;
            }
            return rebuildEntry(settings, request.getTtl(), start);
        });
        long computeMicros = (System.nanoTime() - start) / 1_000;
        missCounter.increment();
        rebuildCounter.increment();
        evictOverflow(map, settings.getMaxEntries());
        return new CacheReadResult(key, CacheReadResult.Source.MISS_REBUILT, map.size(), computeMicros);
    }

    public CacheInvalidateResult invalidate(ConcurrentCacheRequest request) {
        applySettings(request.getSettings());
        ConcurrentHashMap<String, CacheEntry> map = storeRef.get();
        int range = Math.min(request.getInvalidateRange(), map.size());
        int removed = 0;
        Iterator<String> iterator = map.keySet().iterator();
        while (iterator.hasNext() && removed < range) {
            iterator.next();
            iterator.remove();
            removed++;
        }
        invalidateCounter.increment(range);
        return new CacheInvalidateResult(range, removed, map.size());
    }

    public CacheRefreshResult refresh(ConcurrentCacheRequest request) {
        applySettings(request.getSettings());
        ConcurrentHashMap<String, CacheEntry> map = storeRef.get();
        ConcurrentCacheSettings settings = settingsRef.get();
        int batch = request.getRefreshBatch();
        long totalMicros = 0;
        for (int i = 0; i < batch; i++) {
            String key = selectKey(settings, request.getKeyPrefix());
            long start = System.nanoTime();
            CacheEntry rebuilt = rebuildEntry(settings, request.getTtl(), start);
            map.put(key, rebuilt);
            totalMicros += (System.nanoTime() - start) / 1_000;
        }
        refreshCounter.increment(batch);
        evictOverflow(map, settings.getMaxEntries());
        return new CacheRefreshResult(batch, map.size(), totalMicros);
    }

    private void applySettings(ConcurrentCacheSettings incoming) {
        ConcurrentCacheSettings current = settingsRef.get();
        ConcurrentCacheSettings normalized = incoming.normalized();
        if (!current.equals(normalized)) {
            settingsRef.set(normalized);
            if (normalized.isResetCache()) {
                storeRef.set(new ConcurrentHashMap<>(normalized.getInitialCapacity(),
                        (float) normalized.getLoadFactor(), normalized.getConcurrencyLevel()));
                log.info("cache {} reset with new settings {}", cacheName, normalized);
            }
        }
    }

    private CacheEntry rebuildEntry(ConcurrentCacheSettings settings, Duration ttl, long startNanos) {
        busyWaitMicros(settings.getComputeCostMicros());
        byte[] payload = new byte[settings.getValueSizeBytes()];
        ThreadLocalRandom.current().nextBytes(payload);
        long expiresAt = System.nanoTime() + ttl.toNanos();
        long durationNanos = Math.max(1, System.nanoTime() - startNanos);
        computeTimer.record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        return new CacheEntry(payload, expiresAt);
    }

    private void evictOverflow(ConcurrentHashMap<String, CacheEntry> map, int maxEntries) {
        int over = map.size() - maxEntries;
        if (over <= 0) {
            return;
        }
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry>> iterator = map.entrySet().iterator();
        while (iterator.hasNext() && removed < over) {
            iterator.next();
            iterator.remove();
            removed++;
        }
        evictionCounter.increment(removed);
    }

    private String selectKey(ConcurrentCacheSettings settings, String prefix) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (settings.getHotKeyCount() > 0 && random.nextDouble() < settings.getHotKeyRatio()) {
            int hot = random.nextInt(settings.getHotKeyCount());
            return prefix + "hot-" + hot;
        }
        int key = random.nextInt(settings.getKeySpaceSize());
        return prefix + "key-" + key;
    }

    private void busyWaitMicros(int micros) {
        if (micros <= 0) {
            return;
        }
        long deadline = System.nanoTime() + micros * 1_000L;
        while (System.nanoTime() < deadline) {
            // busy spin to simulate CPU work
            LockSupport.parkNanos(0L);
        }
    }

    private void registerMeters() {
        Gauge.builder("concurrent_cache_size", this, engine -> engine.storeRef.get().size())
                .description("Current map size for concurrent cache experiment")
                .tag("cache", cacheName)
                .register(meterRegistry);
        Gauge.builder("concurrent_cache_max_entries", this, engine -> engine.settingsRef.get().getMaxEntries())
                .description("Configured max entries for concurrent cache experiment")
                .tag("cache", cacheName)
                .register(meterRegistry);
        Gauge.builder("concurrent_cache_value_bytes", this, engine -> engine.settingsRef.get().getValueSizeBytes())
                .description("Value size used when rebuilding cache entries")
                .tag("cache", cacheName)
                .register(meterRegistry);
    }

    @Value
    private static class CacheEntry {
        byte[] payload;
        long expiresAtNanos;

        boolean isExpired(long now) {
            return now >= expiresAtNanos;
        }
    }

    @Value
    public static class CacheReadResult {
        String key;
        Source source;
        int mapSize;
        long computeMicros;

        public enum Source {
            HIT,
            MISS_REBUILT
        }
    }

    @Value
    public static class CacheInvalidateResult {
        int requested;
        int removed;
        int remainingSize;
    }

    @Value
    public static class CacheRefreshResult {
        int refreshed;
        int mapSize;
        long totalMicros;
    }
}
