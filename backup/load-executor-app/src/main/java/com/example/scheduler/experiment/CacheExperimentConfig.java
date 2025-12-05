package com.example.scheduler.experiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis+MySQL缓存实验配置
 * 用于测试缓存命中率、雪崩、击穿、穿透等场景
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheExperimentConfig {
    
    /**
     * MySQL初始化配置
     */
    private MysqlInitConfig mysqlInit;
    
    /**
     * Redis初始化配置
     */
    private RedisInitConfig redisInit;
    
    /**
     * 读压测配置
     */
    private ReadLoadConfig readLoad;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MysqlInitConfig {
        /**
         * MySQL用户数（从1开始）
         */
        private long userCount;
        
        /**
         * 每用户自选数
         */
        private int favPerUser;
        
        /**
         * 批次大小
         */
        private int batchSize;
        
        /**
         * 是否清空已有数据
         */
        private boolean truncateFirst;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedisInitConfig {
        /**
         * Redis用户数（从1开始，需与MySQL重合）
         */
        private long userCount;
        
        /**
         * 每用户自选数
         */
        private int favPerUser;
        
        /**
         * 批次大小
         */
        private int batchSize;
        
        /**
         * Key前缀
         */
        private String keyPrefix;
        
        /**
         * TTL（秒），null表示不设置
         */
        private Integer ttlSeconds;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadLoadConfig {
        /**
         * 热点流量占比
         */
        private double hotShare;
        
        /**
         * ID分布：uniform / zipf
         */
        private String idDistribution;
        
        /**
         * Zipf参数s
         */
        private double zipfS;
        
        /**
         * 目标QPS（0不限速）
         */
        private int qps;
        
        /**
         * 并发线程数
         */
        private int concurrency;
        
        /**
         * 读取TopN
         */
        private int topN;
        
        /**
         * 缓存策略
         */
        private Experiment.CacheStrategy cacheStrategy;
        
        /**
         * Key前缀
         */
        private String keyPrefix;
    }
}
