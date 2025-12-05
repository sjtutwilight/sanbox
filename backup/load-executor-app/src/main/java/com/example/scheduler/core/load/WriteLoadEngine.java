package com.example.scheduler.core.load;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.experiment.LoadTaskStatus;
import com.example.scheduler.service.WriteLoadOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Core engine for continuous write workloads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WriteLoadEngine {

    private final WriteLoadOrchestrator writeLoadOrchestrator;

    public void run(LoadTask task, DataGenerationRequest request) {
        try {
            log.info("启动持续写入任务: {}", task.getId());
            long startTime = System.currentTimeMillis();
            long lastStatTime = startTime;
            long lastOps = 0;
            int batchSize = request.getBatchSize() != null ? request.getBatchSize() : 1000;
            int targetQps = request.getQps() != null ? request.getQps() : 0;
            long intervalNanos = targetQps > 0 ? (long) (1_000_000_000.0 / targetQps) : 0;

            while (!task.shouldStop()) {
                try {
                    long batchStart = System.nanoTime();
                    int written = writeLoadOrchestrator.writeBatch(request, batchSize);
                    long batchEnd = System.nanoTime();
                    double latencyMs = (batchEnd - batchStart) / 1_000_000.0;
                    task.incrementOps(written);
                    updateLatencyStats(task, latencyMs);
                    long now = System.currentTimeMillis();
                    if (now - lastStatTime >= 1000) {
                        long currentOps = task.getTotalOpsValue();
                        double opsPerSec = (currentOps - lastOps) * 1000.0 / (now - lastStatTime);
                        task.setCurrentOpsPerSec(opsPerSec);
                        lastOps = currentOps;
                        lastStatTime = now;
                    }
                    if (intervalNanos > 0) {
                        long elapsed = batchEnd - batchStart;
                        if (elapsed < intervalNanos) {
                            TimeUnit.NANOSECONDS.sleep(intervalNanos - elapsed);
                        }
                    } else {
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    task.incrementErrors();
                    task.setLastError(e.getMessage());
                    log.warn("写入批次失败: {}", e.getMessage());
                }
            }
            task.setStatus(LoadTaskStatus.STOPPED);
        } catch (Exception e) {
            log.error("持续写入任务失败: {}", task.getId(), e);
            task.setStatus(LoadTaskStatus.FAILED);
            task.setLastError(e.getMessage());
        } finally {
            task.setEndedAt(Instant.now());
        }
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
}
