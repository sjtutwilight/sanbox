package com.example.scheduler.core.load;

import com.example.scheduler.experiment.Experiment;
import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.experiment.LoadTaskStatus;
import com.example.scheduler.service.ReadLoadOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Core engine for continuous read workloads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReadLoadEngine {

    private final ReadLoadOrchestrator readLoadOrchestrator;

    public void run(LoadTask task, Experiment.ReadLoadConfig config) {
        try {
            log.info("启动持续读取任务: {}", task.getId());
            ExecutorService readExecutor = Executors.newFixedThreadPool(config.getConcurrency());
            double hotShare = config.getHotShare() > 0 ? config.getHotShare() : 0.0;
            int totalUsers = Math.max(1, config.getUserCount());
            int hotSpan = Math.max(1, (int) Math.round(totalUsers * 0.1));
            int coldSpan = Math.max(1, totalUsers - hotSpan);
            ZipfSampler hotSampler = buildZipfSampler(hotSpan, config);
            int targetQps = config.getQps() > 0 ? config.getQps() : 0;
            long intervalNanos = targetQps > 0 ? (long) (1_000_000_000.0 * config.getConcurrency() / targetQps) : 0;
            Experiment.CacheStrategy cacheStrategy = config.getCacheStrategy() != null
                    ? config.getCacheStrategy()
                    : Experiment.CacheStrategy.DIRECT_REDIS;

            long startTime = System.currentTimeMillis();
            long lastStatTime = startTime;
            long lastOps = 0;

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < config.getConcurrency(); i++) {
                futures.add(readExecutor.submit(() -> {
                    Random workerRandom = new Random();
                    while (!task.shouldStop()) {
                        try {
                            long opStart = System.nanoTime();
                            int userId = sampleUserId(hotShare, totalUsers, hotSpan, coldSpan, hotSampler, workerRandom);
                            readLoadOrchestrator.performRead(config, cacheStrategy, userId);
                            long opEnd = System.nanoTime();
                            double latencyMs = (opEnd - opStart) / 1_000_000.0;
                            task.incrementOps();
                            updateLatencyStats(task, latencyMs);
                            if (intervalNanos > 0) {
                                long elapsed = opEnd - opStart;
                                if (elapsed < intervalNanos) {
                                    TimeUnit.NANOSECONDS.sleep(intervalNanos - elapsed);
                                }
                            }
                        } catch (Exception e) {
                            task.incrementErrors();
                            task.setLastError(e.getMessage());
                        }
                    }
                }));
            }

            while (!task.shouldStop()) {
                try {
                    Thread.sleep(1000);
                    long now = System.currentTimeMillis();
                    long currentOps = task.getTotalOpsValue();
                    double opsPerSec = (currentOps - lastOps) * 1000.0 / (now - lastStatTime);
                    task.setCurrentOpsPerSec(opsPerSec);
                    lastOps = currentOps;
                    lastStatTime = now;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            futures.forEach(f -> f.cancel(true));
            readExecutor.shutdownNow();
            task.setStatus(LoadTaskStatus.STOPPED);
        } catch (Exception e) {
            log.error("持续读取任务失败: {}", task.getId(), e);
            task.setStatus(LoadTaskStatus.FAILED);
            task.setLastError(e.getMessage());
        } finally {
            task.setEndedAt(Instant.now());
        }
    }

    private int sampleUserId(double hotShare, int totalUsers, int hotSpan, int coldSpan,
                              ZipfSampler hotSampler, Random random) {
        boolean chooseHot = random.nextDouble() < hotShare;
        if (chooseHot && hotSpan > 0) {
            if (hotSampler != null) {
                long rank = hotSampler.sample(random);
                return (int) Math.min(totalUsers, Math.max(1, rank));
            }
            return 1 + random.nextInt(Math.max(1, hotSpan));
        }
        int coldStart = Math.min(totalUsers, Math.max(1, hotSpan + 1));
        int span = Math.max(1, coldSpan);
        if (hotShare >= 1.0 || coldSpan <= 0) {
            return 1 + random.nextInt(totalUsers);
        }
        return coldStart + random.nextInt(span);
    }

    private ZipfSampler buildZipfSampler(int span, Experiment.ReadLoadConfig config) {
        if (!"zipf".equalsIgnoreCase(config.getIdDistribution())) {
            return null;
        }
        long boundedSpan = Math.max(1, Math.min(100000, span));
        double s = config.getZipfS() > 0 ? config.getZipfS() : 1.1;
        return new ZipfSampler(boundedSpan, s);
    }

    private synchronized void updateLatencyStats(LoadTask task, double latencyMs) {
        long ops = task.getTotalOpsValue();
        if (ops == 1) {
            task.setAvgLatencyMs(latencyMs);
            task.setMaxLatencyMs((long) latencyMs);
        } else {
            double currentAvg = task.getAvgLatencyMs();
            task.setAvgLatencyMs(currentAvg + (latencyMs - currentAvg) / ops);
            if (latencyMs > task.getMaxLatencyMs()) {
                task.setMaxLatencyMs((long) latencyMs);
            }
        }
    }

    /**
     * Simplified Zipf sampler reused from the prior implementation.
     */
    private static class ZipfSampler {
        private final double[] cumulativeProbabilities;

        ZipfSampler(long n, double s) {
            n = Math.min(n, 100000);
            cumulativeProbabilities = new double[(int) n];
            double harmonicSum = 0;
            for (int k = 1; k <= n; k++) {
                harmonicSum += 1.0 / Math.pow(k, s);
            }
            double cumulative = 0;
            for (int k = 1; k <= n; k++) {
                cumulative += (1.0 / Math.pow(k, s)) / harmonicSum;
                cumulativeProbabilities[k - 1] = cumulative;
            }
        }

        long sample(Random random) {
            double r = random.nextDouble();
            int low = 0, high = cumulativeProbabilities.length - 1;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (cumulativeProbabilities[mid] < r) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            return low + 1;
        }
    }
}
