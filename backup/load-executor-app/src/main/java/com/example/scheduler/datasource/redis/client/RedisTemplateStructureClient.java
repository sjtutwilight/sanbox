package com.example.scheduler.datasource.redis.client;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link RedisStructureClient} backed by Spring's {@link RedisTemplate}.
 */
@Component
@RequiredArgsConstructor
public class RedisTemplateStructureClient implements RedisStructureClient {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void pipeline(Consumer<StructureOperations> callback) {
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                callback.accept(new DefaultStructureOperations((RedisOperations<String, Object>) operations));
                return null;
            }
        });
    }

    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private static class DefaultStructureOperations implements StructureOperations {
        private final RedisOperations<String, Object> operations;

        private DefaultStructureOperations(RedisOperations<String, Object> operations) {
            this.operations = operations;
        }

        @Override
        public void putAllHash(String key, Map<String, Object> payload) {
            operations.opsForHash().putAll(key, payload);
        }

        @Override
        public void putHashField(String key, String field, Object payload) {
            operations.opsForHash().put(key, field, payload);
        }

        @Override
        public void leftPush(String key, Object value) {
            operations.opsForList().leftPush(key, value);
        }

        @Override
        public void trimList(String key, int start, int end) {
            operations.opsForList().trim(key, start, end);
        }

        @Override
        public void addToSet(String key, Object value) {
            operations.opsForSet().add(key, value);
        }

        @Override
        public void addAllToSet(String key, Collection<?> values) {
            if (values == null || values.isEmpty()) {
                return;
            }
            operations.opsForSet().add(key, values.toArray());
        }

        @Override
        public void addToZset(String key, Object value, double score) {
            operations.opsForZSet().add(key, value, score);
        }

        @Override
        public void setBit(String key, long offset, boolean value) {
            operations.opsForValue().setBit(key, offset, value);
        }

        @Override
        public void expire(String key, Integer ttlSeconds) {
            if (ttlSeconds != null && ttlSeconds > 0) {
                operations.expire(key, ttlSeconds, TimeUnit.SECONDS);
            }
        }
    }
}
