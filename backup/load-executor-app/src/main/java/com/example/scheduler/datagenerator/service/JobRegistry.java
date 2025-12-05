package com.example.scheduler.datagenerator.service;

import com.example.scheduler.datagenerator.model.DataGenerationJob;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的内存任务注册表
 */
@Component
public class JobRegistry {

    private final Map<String, DataGenerationJob> jobs = new ConcurrentHashMap<>();

    public void save(DataGenerationJob job) {
        jobs.put(job.getId(), job);
    }

    public Optional<DataGenerationJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }
}
