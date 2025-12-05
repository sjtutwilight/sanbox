package com.example.scheduler.core.load;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datasource.redis.RedisDataWriter;
import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.experiment.LoadTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Core engine for initialization workloads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InitDataEngine {

    private final RedisDataWriter redisDataWriter;

    public void run(LoadTask task, DataGenerationRequest request) {
        try {
            log.info("启动初始化数据任务: {}", task.getId());
            redisDataWriter.generateWithCallback(request, (written, failed) -> {
                task.incrementWritten(written);
                if (failed > 0) {
                    task.getErrors().addAndGet(failed);
                }
                return !task.shouldStop();
            });
            if (task.shouldStop()) {
                task.setStatus(LoadTaskStatus.STOPPED);
            } else {
                task.setStatus(LoadTaskStatus.COMPLETED);
            }
            log.info("初始化完成, written={} / target={}", task.getWrittenValue(), task.getTarget());
        } catch (Exception e) {
            log.error("初始化数据任务失败: {}", task.getId(), e);
            task.setStatus(LoadTaskStatus.FAILED);
            task.setLastError(e.getMessage());
        } finally {
            task.setEndedAt(Instant.now());
        }
    }
}
