package com.example.scheduler.metrics;

import io.micrometer.core.instrument.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义指标
 * 用于Prometheus监控
 */
@Component
@Getter
public class CustomMetrics {
    
    private final Counter dataWriteCounter;
    private final Counter dataWriteSuccessCounter;
    private final Counter dataWriteFailureCounter;
    private final Timer dataWriteTimer;
    private final AtomicLong redisKeyCount;
    private final AtomicLong lastWriteTimestamp;
    private final DistributionSummary dataValueDistribution;
    
    public CustomMetrics(MeterRegistry meterRegistry) {
        // 数据写入总次数(Counter)
        this.dataWriteCounter = Counter.builder("scheduler_data_write_total")
                .description("数据写入总次数")
                .tag("application", "scheduler-redis-monitor")
                .register(meterRegistry);
        
        // 数据写入成功次数(Counter)
        this.dataWriteSuccessCounter = Counter.builder("scheduler_data_write_success_total")
                .description("数据写入成功次数")
                .tag("application", "scheduler-redis-monitor")
                .register(meterRegistry);
        
        // 数据写入失败次数(Counter)
        this.dataWriteFailureCounter = Counter.builder("scheduler_data_write_failure_total")
                .description("数据写入失败次数")
                .tag("application", "scheduler-redis-monitor")
                .register(meterRegistry);
        
        // 数据写入耗时(Timer)
        this.dataWriteTimer = Timer.builder("scheduler_data_write_duration_seconds")
                .description("数据写入耗时(秒)")
                .tag("application", "scheduler-redis-monitor")
                .register(meterRegistry);
        
        // Redis Key数量(Gauge)
        this.redisKeyCount = new AtomicLong(0);
        Gauge.builder("scheduler_redis_keys_total", redisKeyCount, AtomicLong::get)
                .description("Redis中的Key总数")
                .tag("application", "scheduler-redis-monitor")
                .register(meterRegistry);
        
        // 最后写入时间戳(Gauge)
        this.lastWriteTimestamp = new AtomicLong(0);
        Gauge.builder("scheduler_last_write_timestamp_seconds", lastWriteTimestamp, AtomicLong::get)
                .description("最后一次写入的时间戳(秒)")
                .tag("application", "scheduler-redis-monitor")
                .register(meterRegistry);
        
        // 数据值分布(DistributionSummary)
        this.dataValueDistribution = DistributionSummary.builder("scheduler_data_value_distribution")
                .description("写入数据值的分布")
                .tag("application", "scheduler-redis-monitor")
                .register(meterRegistry);
    }
    
    /**
     * 记录数据写入
     */
    public void recordDataWrite(int successCount, int totalCount) {
        dataWriteCounter.increment(totalCount);
        dataWriteSuccessCounter.increment(successCount);
        if (successCount < totalCount) {
            dataWriteFailureCounter.increment(totalCount - successCount);
        }
        // 更新最后写入时间戳
        lastWriteTimestamp.set(System.currentTimeMillis() / 1000);
    }
    
    /**
     * 更新Redis Key数量
     */
    public void updateRedisKeyCount(long count) {
        redisKeyCount.set(count);
    }
    
    /**
     * 记录数据值
     */
    public void recordDataValue(double value) {
        dataValueDistribution.record(value);
    }
}
