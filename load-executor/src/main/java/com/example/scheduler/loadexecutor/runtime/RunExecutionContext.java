package com.example.scheduler.loadexecutor.runtime;

import com.example.scheduler.loadexecutor.datasource.redis.RedisStrategy;
import lombok.Builder;
import lombok.Value;

/**
 * 运行时上下文，承载一次负载执行的 profile 与观测维度。
 */
@Value
@Builder
public class RunExecutionContext {
    String platform;
    String scenario;
    String experimentRunId;
    RedisStrategy redisStrategy;
}
