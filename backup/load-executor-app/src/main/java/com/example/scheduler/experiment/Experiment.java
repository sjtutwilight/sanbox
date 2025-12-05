package com.example.scheduler.experiment;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.experiment.scenario.ScenarioParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 实验实体，包含实验元数据、实验组和操作定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experiment {
    private String id;
    private String name;
    private String description;
    
    /**
     * 实验目标说明
     */
    private String objective;

    /**
     * 架构概览 / 关键组件
     */
    private String architecture;

    /**
     * 观察要点列表
     */
    private List<String> observePoints;

    /**
     * 风险提示 / 误用警告
     */
    private List<String> riskWarnings;

    /**
     * 推荐观测的指标面板/链接说明
     */
    private List<String> metricsToWatch;

    /**
     * 推荐操作步骤/命令
     */
    private List<String> recommendations;
    
    /**
     * 实验组列表
     */
    private List<ExperimentGroup> groups;

    public static Experiment withAutoId(String name, String description, String objective,
                                        List<String> observePoints, List<String> recommendations,
                                        List<ExperimentGroup> groups) {
        return Experiment.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .objective(objective)
                .observePoints(observePoints)
                .recommendations(recommendations)
                .groups(groups)
                .build();
    }

    /**
     * 实验组
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentGroup {
        private String id;
        private String name;
        private String description;
        
        /**
         * 该实验组支持的操作列表
         */
        private List<ExperimentOperation> operations;
        
        public static ExperimentGroup withAutoId(String name, String description, 
                                                  List<ExperimentOperation> operations) {
            return ExperimentGroup.builder()
                    .id(UUID.randomUUID().toString().substring(0, 8))
                    .name(name)
                    .description(description)
                    .operations(operations)
                    .build();
        }
    }

    /**
     * 实验操作
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentOperation {
        private String id;
        
        /**
         * 操作类型
         */
        private OperationType type;
        
        /**
         * 显示名称
         */
        private String label;
        
        /**
         * 操作提示/说明
         */
        private String hint;
        
        /**
         * 数据生成请求配置（用于 INIT_DATA 和 CONTINUOUS_WRITE）
         */
        private DataGenerationRequest request;
        
        /**
         * 读压测配置（用于 CONTINUOUS_READ）
         */
        private ReadLoadConfig readConfig;
        
        /**
         * 场景配置（用于热点Key等高级场景）
         */
        private ScenarioParams scenarioParams;
        
        /**
         * 缓存实验配置（用于MySQL+Redis组合实验）
         */
        private CacheExperimentConfig cacheConfig;
        
        /**
         * 是否支持参数配置（前端是否显示参数编辑面板）
         */
        @Builder.Default
        private boolean configurable = false;
        
        public static ExperimentOperation initData(String id, String label, String hint,
                                                    DataGenerationRequest request) {
            return ExperimentOperation.builder()
                    .id(id)
                    .type(OperationType.INIT_DATA)
                    .label(label)
                    .hint(hint)
                    .request(request)
                    .build();
        }
        
        public static ExperimentOperation continuousWrite(String id, String label, String hint,
                                                           DataGenerationRequest request) {
            return ExperimentOperation.builder()
                    .id(id)
                    .type(OperationType.CONTINUOUS_WRITE)
                    .label(label)
                    .hint(hint)
                    .request(request)
                    .build();
        }
        
        public static ExperimentOperation continuousRead(String id, String label, String hint,
                                                          ReadLoadConfig readConfig) {
            return ExperimentOperation.builder()
                    .id(id)
                    .type(OperationType.CONTINUOUS_READ)
                    .label(label)
                    .hint(hint)
                    .readConfig(readConfig)
                    .build();
        }
        
        public static ExperimentOperation initMysql(String id, String label, String hint,
                                                     CacheExperimentConfig.MysqlInitConfig config) {
            CacheExperimentConfig cacheConfig = CacheExperimentConfig.builder()
                    .mysqlInit(config)
                    .build();
            return ExperimentOperation.builder()
                    .id(id)
                    .type(OperationType.INIT_MYSQL)
                    .label(label)
                    .hint(hint)
                    .cacheConfig(cacheConfig)
                    .build();
        }
        
        public static ExperimentOperation initRedis(String id, String label, String hint,
                                                     CacheExperimentConfig.RedisInitConfig config) {
            CacheExperimentConfig cacheConfig = CacheExperimentConfig.builder()
                    .redisInit(config)
                    .build();
            return ExperimentOperation.builder()
                    .id(id)
                    .type(OperationType.INIT_REDIS)
                    .label(label)
                    .hint(hint)
                    .cacheConfig(cacheConfig)
                    .build();
        }
        
        /**
         * 创建可配置的场景操作（热点Key等）
         */
        public static ExperimentOperation scenarioLoad(String id, String label, String hint,
                                                        ScenarioParams defaultParams) {
            return ExperimentOperation.builder()
                    .id(id)
                    .type(OperationType.CONTINUOUS_READ)
                    .label(label)
                    .hint(hint)
                    .scenarioParams(defaultParams)
                    .configurable(true)
                    .build();
        }
    }

    /**
     * 读压测配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadLoadConfig {
        private String keyPrefix;
        private int userCount;
        private int topN;
        private int concurrency;
        private double hotShare;
        private String idDistribution;
        private double zipfS;
        /**
         * 目标QPS（0表示不限速）
         */
        private int qps;
        /**
         * 缓存策略：直接读Redis或cache-aside回源
         */
        private CacheStrategy cacheStrategy;
        
        /**
         * 读取模式
         */
        private ReadMode readMode;
    }

    /**
     * 读取模式
     */
    public enum ReadMode {
        /**
         * ZREVRANGE 读取 zset
         */
        ZSET_RANGE,
        
        /**
         * SISMEMBER 检查 set 成员
         */
        SET_ISMEMBER,
        
        /**
         * HGETALL 读取整个 hash
         */
        HASH_GETALL,
        
        /**
         * LRANGE 读取 list
         */
        LIST_RANGE,
        
        /**
         * GETBIT 检查 bitmap
         */
        BITMAP_GETBIT,

        /**
         * K线窗口：读取近期 list
         */
        KLINE_LIST_RECENT,

        /**
         * K线窗口：按时间范围读取 ZSet
         */
        KLINE_ZSET_RANGE
    }

    /**
     * 缓存策略
     */
    public enum CacheStrategy {
        DIRECT_REDIS,
        CACHE_ASIDE
    }
}
