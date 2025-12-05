package com.example.scheduler.experiment.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景参数 - 前端传入的简化参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioParams {
    
    /**
     * 场景类型: redis_hotkey, redis_big_value, redis_ttl_churn 等
     */
    private String scenarioType;
    
    /**
     * Key模式，如 "fav:z:${id}"
     */
    private String keyPattern;
    
    /**
     * 热点ID范围 [min, max]
     */
    private long[] hotRange;
    
    /**
     * 冷数据ID范围 [min, max]
     */
    private long[] coldRange;
    
    /**
     * 热点流量占比 (0.0 ~ 1.0)
     */
    @Builder.Default
    private double hotShare = 0.8;
    
    /**
     * ID分布类型: uniform / zipf
     */
    @Builder.Default
    private String idDistribution = "zipf";
    
    /**
     * Zipf参数s
     */
    @Builder.Default
    private double zipfS = 1.1;
    
    /**
     * 操作类型: ZREVRANGE, SISMEMBER, HGETALL 等
     */
    @Builder.Default
    private String operationType = "ZREVRANGE";
    
    /**
     * 读取TopN（用于ZREVRANGE等）
     */
    @Builder.Default
    private int topN = 50;
    
    /**
     * 目标QPS
     */
    @Builder.Default
    private int qps = 1000;
    
    /**
     * 并发线程数
     */
    @Builder.Default
    private int concurrency = 16;
    
    /**
     * 持续时间（秒），0表示持续运行
     */
    @Builder.Default
    private int durationSeconds = 0;
    
    /**
     * 默认自选集合热点读场景参数
     */
    public static ScenarioParams favoriteHotKeyDefault() {
        return ScenarioParams.builder()
                .scenarioType("redis_hotkey")
                .keyPattern("exp:fav:normal:fav:z:${id}")
                .hotRange(new long[]{1, 1000})
                .coldRange(new long[]{1001, 100000})
                .hotShare(0.8)
                .idDistribution("zipf")
                .zipfS(1.1)
                .operationType("ZREVRANGE")
                .topN(50)
                .qps(2000)
                .concurrency(16)
                .durationSeconds(0)
                .build();
    }
}

