package com.example.scheduler.loadexecutor.experiment.wallet;

import com.example.scheduler.loadexecutor.datasource.redis.RedisDataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletSnapshotCache {

    private final RedisDataSource redisDataSource;
    private final ObjectMapper objectMapper;
    private final WalletExperimentProperties properties;

    public Optional<WalletSnapshot> get(long userId) {
        return Optional.ofNullable(redisDataSource.query(template ->
                        template.opsForValue().get(key(userId))))
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, WalletSnapshot.class));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize wallet snapshot for user {}", userId, e);
                        return Optional.empty();
                    }
                });
    }

    public void put(WalletSnapshot snapshot, Duration ttl) {
        redisDataSource.execute(template -> {
            try {
                String json = objectMapper.writeValueAsString(snapshot);
                if (ttl != null && !ttl.isZero()) {
                    template.opsForValue().set(key(snapshot.getUserId()), json, ttl);
                } else {
                    template.opsForValue().set(key(snapshot.getUserId()), json);
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize wallet snapshot", e);
            }
        });
    }

    public void evict(long userId) {
        redisDataSource.execute(template -> template.delete(key(userId)));
    }

    private String key(long userId) {
        return properties.getCacheKeyPrefix() + ":" + userId;
    }
}
