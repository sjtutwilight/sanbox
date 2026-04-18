package com.example.scheduler.loadexecutor.datasource.redis;

import com.example.scheduler.loadexecutor.runtime.ExecutionTagSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 基于 Lettuce 的 Redis 数据源，实现按 profile 路由到不同连接策略。
 */
@Slf4j
@Component
public class LettuceRedisDataSource implements RedisDataSource {

    private final MeterRegistry meterRegistry;
    private final RedisStrategyResolver strategyResolver;
    private final Map<RedisStrategy, StringRedisTemplate> templates;

    public LettuceRedisDataSource(
            MeterRegistry meterRegistry,
            RedisStrategyResolver strategyResolver,
            @Qualifier("standaloneStringRedisTemplate") StringRedisTemplate standaloneTemplate,
            @Qualifier("sentinelStringRedisTemplate") StringRedisTemplate sentinelTemplate,
            @Qualifier("clusterStringRedisTemplate") StringRedisTemplate clusterTemplate) {
        this.meterRegistry = meterRegistry;
        this.strategyResolver = strategyResolver;
        this.templates = new EnumMap<>(RedisStrategy.class);
        this.templates.put(RedisStrategy.STANDALONE, standaloneTemplate);
        this.templates.put(RedisStrategy.SENTINEL, sentinelTemplate);
        this.templates.put(RedisStrategy.CLUSTER, clusterTemplate);
    }

    private Timer commandTimer() {
        return Timer.builder("redis_command_latency")
                .description("Latency of Redis commands executed via datasource")
                .tag("client", "lettuce")
                .tag("platform", ExecutionTagSupport.platform())
                .tag("scenario", ExecutionTagSupport.scenario())
                .tag("experimentRunId", ExecutionTagSupport.experimentRunId())
                .register(meterRegistry);
    }

    private Counter errorCounter() {
        return Counter.builder("redis_command_errors")
                .description("Errors thrown when executing Redis commands")
                .tag("client", "lettuce")
                .tag("platform", ExecutionTagSupport.platform())
                .tag("scenario", ExecutionTagSupport.scenario())
                .tag("experimentRunId", ExecutionTagSupport.experimentRunId())
                .register(meterRegistry);
    }

    @Override
    public <T> T query(Function<StringRedisTemplate, T> callback) {
        Objects.requireNonNull(callback, "callback");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return callback.apply(resolveTemplate());
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
            callback.accept(resolveTemplate());
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

    /**
     * 根据当前运行 profile 选择 Redis 模板。
     */
    private StringRedisTemplate resolveTemplate() {
        RedisStrategy strategy = strategyResolver.resolve();
        StringRedisTemplate template = templates.get(strategy);
        if (template == null) {
            throw new IllegalStateException("No Redis template configured for strategy " + strategy);
        }
        return template;
    }
}
