package com.example.scheduler.loadexecutor.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "load.executor")
public class LoadExecutorProperties {

    /**
     * 调度 tick 周期（毫秒），即多长时间计算一次需要发起的请求数。
     */
    @Min(10)
    @Max(1000)
    private long tickMillis = 100;

    /**
     * 默认的最大并发数，当 Command 未显式指定 concurrency 时使用。
     */
    @Min(1)
    private int defaultMaxConcurrency = 64;

    /**
     * Worker 线程池配置，用于执行实际的 experiment 调用。
     */
    @NotNull
    private Worker worker = new Worker();

    @Data
    public static class Worker {
        @Min(1)
        private int minThreads = 4;
        @Min(1)
        private int maxThreads = 256;
        @Min(10)
        private int queueCapacity = 2000;
        @Min(1)
        private long keepAliveSeconds = 60;
    }
}
