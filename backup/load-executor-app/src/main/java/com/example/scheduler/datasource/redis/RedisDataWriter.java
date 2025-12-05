package com.example.scheduler.datasource.redis;

import com.example.scheduler.datagenerator.model.DataGenerationJob;
import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datagenerator.model.DataGenerationStatus;
import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datagenerator.support.RequestDefaultsResolver;
import com.example.scheduler.datasource.redis.client.RedisStructureClient;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeAppendContext;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.shape.impl.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Entry point that delegates data generation to shape implementations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDataWriter {

    private final RedisStructureClient redisClient;
    private final RequestDefaultsResolver defaultsResolver;

    private final Map<GenerationPattern, RedisDataShape> shapes = new EnumMap<>(GenerationPattern.class);

    @PostConstruct
    void initShapes() {
        register(new PerUserPositionShape());
        register(new GiantPositionShape());
        register(new TradeListShape(GenerationPattern.TRADE_RECENT_LIST, true));
        register(new TradeListShape(GenerationPattern.TRADE_HISTORY_LIST, false));
        register(new ActiveUsersSetShape());
        register(new ActiveUsersBitmapShape());
        register(new FavoritesPerUserShape());
        register(new FavoritesGiantShape());
        register(new KlineSeriesShape(GenerationPattern.KLINE_MULTI_WINDOW_BOUNDED, true));
        register(new KlineSeriesShape(GenerationPattern.KLINE_MULTI_WINDOW_GIANT, false));
    }

    private void register(RedisDataShape shape) {
        shapes.put(shape.pattern(), shape);
    }

    public void generate(DataGenerationJob job, DataGenerationRequest request) {
        RequestDefaults defaults = defaultsResolver.resolve(request);
        long target = resolveTarget(request, defaults.getValueSizeBytes());
        job.setTarget(target);
        log.info("启动Redis数据生成任务 id={}, 模式={}, 目标条数={}, valueSize={}B, batchSize={}",
                job.getId(), request.getPattern(), target, defaults.getValueSizeBytes(), defaults.getBatchSize());
        long cursor = 0;
        while (cursor < target) {
            if (job.isCancelled()) {
                log.warn("任务已被取消, id={}", job.getId());
                break;
            }
            long remaining = target - cursor;
            int currentBatch = (int) Math.min(defaults.getBatchSize(), remaining);
            try {

                executeWrite(request.getPattern(), RedisDataShapeContext.builder()
                        .redis(redisClient)
                        .request(request)
                        .defaults(defaults)
                        .startIndex(cursor)
                        .count(currentBatch)
                        .build());

                cursor += currentBatch;
                job.incrementWritten(currentBatch);
            } catch (Exception e) {
                job.incrementFailures(currentBatch);
                job.setLastError(e.getMessage());
                job.setStatus(DataGenerationStatus.FAILED);
                log.error("批次写入失败，jobId={}, cursor={}, batch={}", job.getId(), cursor, currentBatch, e);
                break;
            }
        }
        if (job.isCancelled()) {
            job.setStatus(DataGenerationStatus.CANCELLED);
        }
    }

    public void generateWithCallback(DataGenerationRequest request,
                                     java.util.function.BiFunction<Long, Long, Boolean> callback) {
        RequestDefaults defaults = defaultsResolver.resolve(request);
        long target = resolveTarget(request, defaults.getValueSizeBytes());
        long cursor = 0;
        while (cursor < target) {
            long remaining = target - cursor;
            int currentBatch = (int) Math.min(defaults.getBatchSize(), remaining);
            long failed = 0;
            try {
                executeWrite(request.getPattern(), RedisDataShapeContext.builder()
                        .redis(redisClient)
                        .request(request)
                        .defaults(defaults)
                        .startIndex(cursor)
                        .count(currentBatch)
                        .build());
                cursor += currentBatch;
            } catch (Exception e) {
                failed = currentBatch;
                log.error("批次写入失败，cursor={}, batch={}", cursor, currentBatch, e);
            }
            if (!callback.apply((long) currentBatch, failed)) {
                break;
            }
        }
    }

    public int writeBatch(DataGenerationRequest request, int batchSize) {
        RequestDefaults defaults = defaultsResolver.resolve(request);
        long cursor = ThreadLocalRandom.current().nextLong(0, 100_000_000);
        RedisDataShape shape = requireShape(request.getPattern());
        if (shape.supportsAppend()) {
            shape.append(RedisDataShapeAppendContext.builder()
                    .redis(redisClient)
                    .request(request)
                    .defaults(defaults)
                    .batchSize(batchSize)
                    .build());
            return batchSize;
        }
        shape.write(RedisDataShapeContext.builder()
                .redis(redisClient)
                .request(request)
                .defaults(defaults)
                .startIndex(cursor)
                .count(batchSize)
                .build());
        return batchSize;
    }

    public void writeFavoritesFromDb(String keyPrefix, long userId, java.util.List<String> favorites) {
        redisClient.pipeline(ops -> {
            String setKey = keyPrefix + "fav:set:" + userId;
            String zsetKey = keyPrefix + "fav:z:" + userId;
            long now = Instant.now().toEpochMilli();
            int idx = 0;
            for (String sym : favorites) {
                ops.addToSet(setKey, sym);
                ops.addToZset(zsetKey, sym, now - idx);
                idx++;
            }
            ops.expire(setKey, 60);
            ops.expire(zsetKey, 60);
        });
    }

    private void executeWrite(GenerationPattern pattern, RedisDataShapeContext context) {
        RedisDataShape shape = requireShape(pattern);
        shape.write(context);
    }

    private RedisDataShape requireShape(GenerationPattern pattern) {
        RedisDataShape shape = shapes.get(pattern);
        if (shape == null) {
            throw new IllegalArgumentException("未处理的模式: " + pattern);
        }
        return shape;
    }

    private long resolveTarget(DataGenerationRequest request, int valueSizeBytes) {
        long byCount = request.getRecordCount() != null ? request.getRecordCount() : 0;
        long bySize = 0;
        if (request.getTargetSizeMb() != null) {
            long totalBytes = request.getTargetSizeMb() * 1024L * 1024L;
            bySize = totalBytes / Math.max(1, valueSizeBytes);
        }
        long target = Math.max(byCount, bySize);
        return target > 0 ? target : 1;
    }
}
