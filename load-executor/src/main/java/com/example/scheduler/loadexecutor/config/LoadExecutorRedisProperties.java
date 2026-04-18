package com.example.scheduler.loadexecutor.config;

import com.example.scheduler.loadexecutor.datasource.redis.RedisStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis profile 配置，负责将 scenario 映射到具体连接策略。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "load.executor.redis")
public class LoadExecutorRedisProperties {

    /**
     * 未命中 scenario 映射时使用的默认策略。
     */
    @NotNull
    private RedisStrategy defaultStrategy = RedisStrategy.STANDALONE;

    /**
     * scenario 到 Redis 策略的映射表。
     */
    @Valid
    private Map<String, RedisStrategy> scenarioStrategies = new HashMap<>();

    /**
     * 单机模式连接参数。
     */
    @Valid
    @NotNull
    private Standalone standalone = new Standalone();

    /**
     * 哨兵模式连接参数。
     */
    @Valid
    @NotNull
    private Sentinel sentinel = new Sentinel();

    /**
     * 集群模式连接参数。
     */
    @Valid
    @NotNull
    private Cluster cluster = new Cluster();

    @Data
    public static class Standalone {
        private String host = "localhost";
        private int port = 6379;
        private int database = 0;
        private String username;
        private String password;
    }

    @Data
    public static class Sentinel {
        private String master;
        private List<String> nodes = List.of();
        private int database = 0;
        private String username;
        private String password;
    }

    @Data
    public static class Cluster {
        private List<String> nodes = List.of();
        private Integer maxRedirects = 3;
        private String username;
        private String password;
    }
}
