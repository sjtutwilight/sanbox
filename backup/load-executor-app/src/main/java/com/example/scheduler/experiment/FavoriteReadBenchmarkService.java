package com.example.scheduler.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * 自选收藏读取压测：并发读取 zset (按时间排序) + set 存在判定
 */
@Service
@RequiredArgsConstructor
public class FavoriteReadBenchmarkService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Map<String, Object> runReadTest(String keyPrefix, int userCount, int topN, int concurrency, int iterations) {
        AtomicLong totalLatencyMs = new AtomicLong();
        AtomicLong maxLatencyMs = new AtomicLong();
        AtomicLong errors = new AtomicLong();

        Instant start = Instant.now();
        CompletableFuture<?>[] futures = IntStream.range(0, concurrency)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < iterations; j++) {
                        long uid = ThreadLocalRandom.current().nextLong(1, userCount + 1);
                        String zKey = keyPrefix + "fav:z:" + uid;
                        String sKey = keyPrefix + "fav:set:" + uid;
                        Instant t0 = Instant.now();
                        try {
                            redisTemplate.opsForZSet().reverseRange(zKey, 0, topN - 1);
                            redisTemplate.opsForSet().isMember(sKey, "SYM1");
                            long cost = Duration.between(t0, Instant.now()).toMillis();
                            totalLatencyMs.addAndGet(cost);
                            maxLatencyMs.updateAndGet(prev -> Math.max(prev, cost));
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                }, executor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        long totalOps = (long) concurrency * iterations * 2; // zrange + isMember
        double avgLatency = totalLatencyMs.get() / (double) (concurrency * iterations);

        return Map.of(
                "durationMs", durationMs,
                "totalOps", totalOps,
                "avgLatencyMs", avgLatency,
                "maxLatencyMs", maxLatencyMs.get(),
                "errors", errors.get()
        );
    }
}
