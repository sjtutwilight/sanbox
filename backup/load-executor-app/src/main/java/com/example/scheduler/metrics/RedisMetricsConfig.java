package com.example.scheduler.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis连接监控指标配置
 * 监控Redis连接的健康状态
 */
@Slf4j
@Component
public class RedisMetricsConfig implements MeterBinder {
    
    private final LettuceConnectionFactory lettuceConnectionFactory;
    private final AtomicInteger redisConnectionStatus;
    
    public RedisMetricsConfig(LettuceConnectionFactory lettuceConnectionFactory) {
        this.lettuceConnectionFactory = lettuceConnectionFactory;
        this.redisConnectionStatus = new AtomicInteger(0);
    }
    
    @Override
    public void bindTo(MeterRegistry registry) {
        // 注册Redis连接状态指标(1=连接正常, 0=连接异常)
        Gauge.builder("redis_connection_status", this, config -> {
            try {
                // 尝试获取连接来检查Redis是否可用
                if (lettuceConnectionFactory.getConnection().ping() != null) {
                    redisConnectionStatus.set(1);
                    return 1;
                }
            } catch (Exception e) {
                log.debug("Redis连接检查失败: {}", e.getMessage());
                redisConnectionStatus.set(0);
            }
            return 0;
        })
        .description("Redis连接状态(1=正常, 0=异常)")
        .tag("redis", "connection")
        .register(registry);
        
        log.info("Redis连接监控指标注册成功");
    }
}
