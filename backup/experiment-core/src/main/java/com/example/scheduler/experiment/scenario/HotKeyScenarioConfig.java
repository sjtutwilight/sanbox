package com.example.scheduler.experiment.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 热点Key场景配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotKeyScenarioConfig {
    
    /**
     * 场景类型标识
     */
    @Builder.Default
    private String scenarioType = "redis_hotkey";
    
    /**
     * Key空间定义列表
     */
    private List<KeySpaceConfig> keySpaces;
    
    /**
     * 操作配置
     */
    private OperationConfig operation;
    
    /**
     * 负载配置
     */
    private LoadConfig load;
    
    /**
     * 操作配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationConfig {
        /**
         * 操作类型: GET, SET, HGETALL, HSET, ZREVRANGE, SADD, SISMEMBER 等
         */
        @Builder.Default
        private String type = "GET";
        
        /**
         * 读取的数量（用于 ZREVRANGE 等）
         */
        @Builder.Default
        private int topN = 50;
        
        /**
         * 写入时的value大小（字节）
         */
        @Builder.Default
        private int valueSizeBytes = 256;
    }
    
    /**
     * 快捷构建方法 - 典型热点读场景
     */
    public static HotKeyScenarioConfig typicalHotKeyRead(
            String keyPattern,
            long hotMin, long hotMax, double hotShare, double zipfS,
            long coldMin, long coldMax,
            String operationType, int topN,
            int qps, int concurrency, int durationSeconds) {
        
        return HotKeyScenarioConfig.builder()
                .scenarioType("redis_hotkey")
                .keySpaces(List.of(
                        KeySpaceConfig.hotSpace("hot_keys", keyPattern, hotMin, hotMax, hotShare, zipfS),
                        KeySpaceConfig.coldSpace("cold_keys", keyPattern, coldMin, coldMax, 1.0 - hotShare)
                ))
                .operation(OperationConfig.builder()
                        .type(operationType)
                        .topN(topN)
                        .build())
                .load(LoadConfig.constantQps(qps, concurrency, durationSeconds))
                .build();
    }
}

