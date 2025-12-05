package com.example.scheduler.service;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datasource.redis.RedisDataWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Delegates write load execution to either write-through or pure Redis writers.
 */
@Service
@RequiredArgsConstructor
public class WriteLoadOrchestrator {

    private final RedisDataWriter redisDataWriter;
    private final WriteThroughFavoriteService writeThroughFavoriteService;

    public int writeBatch(DataGenerationRequest request, int batchSize) {
        if (Boolean.TRUE.equals(request.getWriteThrough())) {
            return writeThroughFavoriteService.writeBatch(request, batchSize);
        }
        return redisDataWriter.writeBatch(request, batchSize);
    }
}
