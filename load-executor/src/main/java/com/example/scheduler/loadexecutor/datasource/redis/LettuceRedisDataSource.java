package com.example.scheduler.loadexecutor.datasource.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class LettuceRedisDataSource implements RedisDataSource {

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    private Timer commandTimer() {
        return Timer.builder("redis_command_latency")
                .description("Latency of Redis commands executed via datasource")
                .tag("client", "lettuce")
                .register(meterRegistry);
    }

    private Counter errorCounter() {
        return Counter.builder("redis_command_errors")
                .description("Errors thrown when executing Redis commands")
                .tag("client", "lettuce")
                .register(meterRegistry);
    }

    @Override
    public <T> T query(Function<StringRedisTemplate, T> callback) {
        Objects.requireNonNull(callback, "callback");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return callback.apply(redisTemplate);
        } catch (RuntimeException e) {
            errorCounter().increment();
            throw e;
        } catch (Exception e) {
            errorCounter().increment();
            throw new RedisOperationException("Redis query failed", e);
        } finally {
            sample.stop(commandTimer());
        }
    }

    @Override
    public void execute(Consumer<StringRedisTemplate> callback) {
        Objects.requireNonNull(callback, "callback");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            callback.accept(redisTemplate);
        } catch (RuntimeException e) {
            errorCounter().increment();
            throw e;
        } catch (Exception e) {
            errorCounter().increment();
            throw new RedisOperationException("Redis command failed", e);
        } finally {
            sample.stop(commandTimer());
        }
    }
}
