package com.example.scheduler.datasource.redis.shape;

import com.example.scheduler.datagenerator.model.GenerationPattern;

/**
 * Represents a specific logical data shape (hash/list/etc).
 */
public interface RedisDataShape {

    GenerationPattern pattern();

    void write(RedisDataShapeContext context);

    default boolean supportsAppend() {
        return false;
    }

    default void append(RedisDataShapeAppendContext context) {
        throw new UnsupportedOperationException("append not supported for pattern " + pattern());
    }
}
