package com.example.scheduler.loadexecutor.experiment.favorite;

import com.example.scheduler.loadexecutor.datasource.redis.RedisDataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteCacheService {

    private final RedisDataSource redisDataSource;
    private final ObjectMapper objectMapper;
    private final FavoriteExperimentProperties properties;

    public Optional<List<String>> getCached(long userId) {
        return Optional.ofNullable(redisDataSource.query(template ->
                template.opsForValue().get(buildKey(userId))))
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, String.class)));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize favorites cache for user {}", userId, e);
                        return Optional.of(Collections.emptyList());
                    }
                });
    }

    public void cache(long userId, List<String> symbols, Duration ttl) {
        redisDataSource.execute(template -> {
            try {
                String json = objectMapper.writeValueAsString(symbols);
                if (ttl != null && !ttl.isZero()) {
                    template.opsForValue().set(buildKey(userId), json, ttl);
                } else {
                    template.opsForValue().set(buildKey(userId), json);
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize favorites cache", e);
            }
        });
    }

    public void evict(long userId) {
        redisDataSource.execute(template -> template.delete(buildKey(userId)));
    }

    public Duration defaultTtl() {
        Duration ttl = properties.getCacheTtl();
        return ttl != null ? ttl : Duration.ofSeconds(30);
    }

    private String buildKey(long userId) {
        return properties.getKeyPrefix() + ":" + userId;
    }
}
