package com.example.scheduler.loadexecutor.datasource.redis;

/**
 * Redis 连接策略枚举，覆盖单机、哨兵与集群模式。
 */
public enum RedisStrategy {
    STANDALONE,
    SENTINEL,
    CLUSTER;

    /**
     * 解析配置中的策略字符串。
     */
    public static RedisStrategy from(String value, RedisStrategy fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return RedisStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
