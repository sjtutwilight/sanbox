package com.example.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Load Executor 客户端配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "load-executor")
public class LoadExecutorProperties {

    /**
     * Load Executor 基础地址（HTTP）。
     */
    private String baseUrl = "http://localhost:9091";

    /**
     * 连接超时时间（毫秒）。
     */
    private long connectTimeoutMs = 2000;

    /**
     * 读取超时时间（毫秒）。
     */
    private long readTimeoutMs = 5000;

    /**
     * 默认的负载形状类型。
     */
    private String defaultShapeType = "CONSTANT";

    /**
     * 当未指定 qps 时使用的默认值。
     */
    private int defaultQps = 1000;

    /**
     * 当未指定并发时使用的默认值。
     */
    private int defaultConcurrency = 16;

    /**
     * 默认持续时间（秒），null 表示不限。
     */
    private Long defaultDurationSeconds;
}
