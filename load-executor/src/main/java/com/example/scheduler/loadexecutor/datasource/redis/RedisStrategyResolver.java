package com.example.scheduler.loadexecutor.datasource.redis;

import com.example.scheduler.loadexecutor.config.LoadExecutorRedisProperties;
import com.example.scheduler.loadexecutor.runtime.RunExecutionContext;
import com.example.scheduler.loadexecutor.runtime.RunExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 根据当前运行的 scenario 解析 Redis 连接策略。
 */
@Component
@RequiredArgsConstructor
public class RedisStrategyResolver {

    private final LoadExecutorRedisProperties properties;

    /**
     * 解析当前线程应使用的 Redis 策略。
     */
    public RedisStrategy resolve() {
        RunExecutionContext context = RunExecutionContextHolder.current();
        if (context == null) {
            return properties.getDefaultStrategy();
        }
        String scenario = normalize(context.getScenario());
        RedisStrategy mapped = properties.getScenarioStrategies().get(scenario);
        return mapped != null ? mapped : properties.getDefaultStrategy();
    }

    /**
     * 规范化 scenario key，保证映射查找稳定。
     */
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
