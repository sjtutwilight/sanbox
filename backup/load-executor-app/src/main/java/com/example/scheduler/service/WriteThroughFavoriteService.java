package com.example.scheduler.service;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datasource.mysql.FavoritePersistenceService;
import com.example.scheduler.datasource.redis.RedisDataWriter;
import com.example.scheduler.service.core.WriteThroughExecutor;
import com.example.scheduler.service.core.WriteThroughPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Favorite-specific write-through built on {@link WriteThroughExecutor}.
 */
@Service
@RequiredArgsConstructor
public class WriteThroughFavoriteService {

    private final FavoritePersistenceService favoritePersistenceService;
    private final RedisDataWriter redisDataWriter;
    private final WriteThroughExecutor writeThroughExecutor;

    public int writeBatch(DataGenerationRequest request, int batchSize) {
        long userCount = request.getUserCount() != null ? request.getUserCount() : 100_000L;
        String keyPrefix = request.getKeyPrefix() != null ? request.getKeyPrefix() : "dg:";
        return writeThroughExecutor.execute(WriteThroughPlan.<FavoritePayload>builder()
                .batchSize(batchSize)
                .payloadSupplier(() -> randomPayload(userCount, keyPrefix))
                .persistenceWriter(payload -> favoritePersistenceService.addFavorite(
                        payload.userId(), payload.symbol(), "wt"))
                .cacheWriter(payload -> redisDataWriter.writeFavoritesFromDb(
                        payload.keyPrefix(), payload.userId(), List.of(payload.symbol())))
                .build());
    }

    private FavoritePayload randomPayload(long userCount, String keyPrefix) {
        long userId = ThreadLocalRandom.current().nextLong(1, userCount + 1);
        String sym = "SYM" + ThreadLocalRandom.current().nextInt(1, 5000);
        return new FavoritePayload(userId, sym, keyPrefix);
    }

    private record FavoritePayload(long userId, String symbol, String keyPrefix) {
    }
}
