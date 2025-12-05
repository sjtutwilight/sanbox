package com.example.scheduler.experiment.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 负载配置 - 定义压测的并发和速率参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadConfig {
    
    /**
     * 负载模式
     */
    @Builder.Default
    private LoadMode mode = LoadMode.CONSTANT_QPS;
    
    /**
     * 目标 QPS（每秒请求数）
     */
    @Builder.Default
    private int qps = 1000;
    
    /**
     * 持续时间（秒），0 表示持续运行直到手动停止
     */
    @Builder.Default
    private int durationSeconds = 0;
    
    /**
     * 并发线程数
     */
    @Builder.Default
    private int concurrency = 16;
    
    /**
     * 预热时间（秒）
     */
    @Builder.Default
    private int warmupSeconds = 5;
    
    /**
     * 负载模式枚举
     */
    public enum LoadMode {
        /**
         * 恒定QPS模式
         */
        CONSTANT_QPS,
        
        /**
         * 最大吞吐模式（尽可能快）
         */
        MAX_THROUGHPUT,
        
        /**
         * 阶梯递增模式
         */
        STEP_INCREMENT
    }
    
    /**
     * 快捷构建方法 - 恒定QPS
     */
    public static LoadConfig constantQps(int qps, int concurrency, int durationSeconds) {
        return LoadConfig.builder()
                .mode(LoadMode.CONSTANT_QPS)
                .qps(qps)
                .concurrency(concurrency)
                .durationSeconds(durationSeconds)
                .build();
    }
    
    /**
     * 快捷构建方法 - 最大吞吐
     */
    public static LoadConfig maxThroughput(int concurrency) {
        return LoadConfig.builder()
                .mode(LoadMode.MAX_THROUGHPUT)
                .concurrency(concurrency)
                .durationSeconds(0)
                .build();
    }
}

