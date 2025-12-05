package com.example.scheduler.service;

import com.example.scheduler.datasource.mysql.FavoritePersistenceService;
import com.example.scheduler.datasource.redis.RedisDataWriter;
import com.example.scheduler.service.core.CacheAsideExecutor;
import com.example.scheduler.service.core.CacheAsidePlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Favorite-specific cache access built on top of generic cache-aside executor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteCacheService {

    private final StringRedisTemplate redisTemplate;
    private final FavoritePersistenceService favoritePersistenceService;
    private final RedisDataWriter redisDataWriter;
    private final CacheAsideExecutor cacheAsideExecutor;

    public List<Object> getFavorites(long userId, String keyPrefix, int topN) {
        log.info("getFavorites: userId={}, keyPrefix={}, topN={}", userId, keyPrefix, topN);
        String zKey = keyPrefix + "fav:z:" + userId;
        List<String> favorites = cacheAsideExecutor.execute(CacheAsidePlan.<String>builder()
                .cacheReader(() -> readZset(zKey, topN))
                .sourceLoader(() -> loadFromDb(userId))
                .cacheWriter(symbols -> redisDataWriter.writeFavoritesFromDb(keyPrefix, userId, symbols))
                .emptyValueWriter(() -> cacheEmpty(keyPrefix, userId))
                .build());
        return favorites.stream().map(o -> (Object) o).toList();
    }

    private List<String> readZset(String zKey, int topN) {
        return redisTemplate.opsForZSet().reverseRange(zKey, 0, topN - 1)
                .stream().map(Object::toString).toList();
    }

    private List<String> loadFromDb(long userId) {
        return favoritePersistenceService.listByUser(userId).stream()
                .map(f -> f.getSymbol()).toList();
    }

    private void cacheEmpty(String keyPrefix, long userId) {
        redisTemplate.opsForValue().set(keyPrefix + "fav:empty:" + userId, "1", 30, TimeUnit.SECONDS);
    }
}
