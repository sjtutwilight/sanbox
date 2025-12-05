package com.example.scheduler.experiment.scenario;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 场景编译器 - 将前端简化参数编译为内部DSL配置
 */
@Component
public class ScenarioCompiler {

    /**
     * 编译场景参数为热点Key配置
     */
    public HotKeyScenarioConfig compileHotKeyScenario(ScenarioParams params) {
        // 确定分布类型
        KeySpaceConfig.IdDistribution dist = "zipf".equalsIgnoreCase(params.getIdDistribution()) 
                ? KeySpaceConfig.IdDistribution.ZIPF 
                : KeySpaceConfig.IdDistribution.UNIFORM;
        
        // 构建热点空间
        KeySpaceConfig.KeySpaceConfigBuilder hotBuilder = KeySpaceConfig.builder()
                .name("hot_keys")
                .pattern(params.getKeyPattern())
                .idRange(params.getHotRange())
                .trafficShare(params.getHotShare())
                .idDistribution(dist);
        
        if (dist == KeySpaceConfig.IdDistribution.ZIPF) {
            hotBuilder.zipfParams(KeySpaceConfig.ZipfParams.builder()
                    .s(params.getZipfS())
                    .build());
        }
        
        // 构建冷数据空间
        KeySpaceConfig coldSpace = KeySpaceConfig.builder()
                .name("cold_keys")
                .pattern(params.getKeyPattern())
                .idRange(params.getColdRange())
                .trafficShare(1.0 - params.getHotShare())
                .idDistribution(KeySpaceConfig.IdDistribution.UNIFORM)
                .build();
        
        return HotKeyScenarioConfig.builder()
                .scenarioType("redis_hotkey")
                .keySpaces(List.of(hotBuilder.build(), coldSpace))
                .operation(HotKeyScenarioConfig.OperationConfig.builder()
                        .type(params.getOperationType())
                        .topN(params.getTopN())
                        .build())
                .load(LoadConfig.builder()
                        .mode(LoadConfig.LoadMode.CONSTANT_QPS)
                        .qps(params.getQps())
                        .concurrency(params.getConcurrency())
                        .durationSeconds(params.getDurationSeconds())
                        .build())
                .build();
    }

    /**
     * 获取场景的默认参数
     */
    public ScenarioParams getDefaultParams(String scenarioType) {
        return switch (scenarioType) {
            case "redis_hotkey", "favorite_hotkey" -> ScenarioParams.favoriteHotKeyDefault();
            default -> ScenarioParams.builder()
                    .scenarioType(scenarioType)
                    .keyPattern("key:${id}")
                    .hotRange(new long[]{1, 100})
                    .coldRange(new long[]{101, 10000})
                    .hotShare(0.8)
                    .qps(1000)
                    .concurrency(16)
                    .build();
        };
    }
}

