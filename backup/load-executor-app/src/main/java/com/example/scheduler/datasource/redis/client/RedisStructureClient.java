package com.example.scheduler.datasource.redis.client;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Minimal abstraction over Redis structural operations used by higher layers.
 */
public interface RedisStructureClient {

    void pipeline(Consumer<StructureOperations> callback);

    boolean hasKey(String key);

    interface StructureOperations {
        void putAllHash(String key, java.util.Map<String, Object> payload);
        void putHashField(String key, String field, Object payload);
        void leftPush(String key, Object value);
        void trimList(String key, int start, int end);
        void addToSet(String key, Object value);
        void addAllToSet(String key, Collection<?> values);
        void addToZset(String key, Object value, double score);
        void setBit(String key, long offset, boolean value);
        void expire(String key, Integer ttlSeconds);
    }
}
