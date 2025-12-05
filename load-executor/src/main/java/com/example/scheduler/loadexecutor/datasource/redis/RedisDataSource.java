package com.example.scheduler.loadexecutor.datasource.redis;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Minimal abstraction over Redis so experiment modules do not touch low-level templates.
 */
public interface RedisDataSource {

    <T> T query(Function<StringRedisTemplate, T> callback);

    void execute(Consumer<StringRedisTemplate> callback);
}
