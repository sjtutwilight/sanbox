package com.example.scheduler.datagenerator.service;

import com.example.scheduler.config.DataGeneratorProperties;
import com.example.scheduler.datagenerator.model.*;
import com.example.scheduler.datagenerator.support.RequestDefaultsResolver;
import com.example.scheduler.datasource.redis.RedisDataWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据生成调度服务：接收请求、创建任务、异步执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService {

    private final JobRegistry jobRegistry;
    private final RedisDataWriter redisDataWriter;
    private final DataGeneratorProperties properties;
    private final RequestDefaultsResolver defaultsResolver;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DataGenerationJob start(DataGenerationRequest request) {
        defaultsResolver.applyDefaults(request);
        String id = UUID.randomUUID().toString();
        DataGenerationJob job = new DataGenerationJob(id, request);
        jobRegistry.save(job);

        executor.submit(() -> runJob(job));
        return job;
    }

    private void runJob(DataGenerationJob job) {
        job.setStartedAt(Instant.now());
        job.setStatus(DataGenerationStatus.RUNNING);
        try {
            if (job.getRequestSnapshot().getDataSource() != DataSourceType.REDIS) {
                throw new IllegalArgumentException("暂不支持的数据源: " + job.getRequestSnapshot().getDataSource());
            }
            redisDataWriter.generate(job, job.getRequestSnapshot());
            if (!job.isCancelled() && job.getStatus() != DataGenerationStatus.FAILED) {
                job.setStatus(DataGenerationStatus.COMPLETED);
            }
        } catch (Exception e) {
            log.error("数据生成任务执行失败, id={}", job.getId(), e);
            job.setStatus(DataGenerationStatus.FAILED);
            job.setLastError(e.getMessage());
        } finally {
            job.setEndedAt(Instant.now());
        }
    }

    public DataGenerationJob cancel(String id) {
        DataGenerationJob job = jobRegistry.find(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
        boolean changed = job.cancel();
        if (changed) {
            job.setStatus(DataGenerationStatus.CANCELLED);
        }
        return job;
    }

    public DataGenerationJob getJob(String id) {
        return jobRegistry.find(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
    }

    public DataGeneratorProperties defaults() {
        return properties;
    }

}
