package com.example.scheduler.loadexecutor.experiment.concurrentmap;

import com.example.scheduler.experiment.OperationType;
import com.example.scheduler.loadexecutor.domain.LoadShapeType;
import com.example.scheduler.loadexecutor.experiment.ExperimentGroup;
import com.example.scheduler.loadexecutor.experiment.ExperimentOperationDefinition;
import com.example.scheduler.loadexecutor.experiment.LoadShapeTemplate;
import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import com.example.scheduler.loadexecutor.experiment.OperationParameter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UdqsPlanExperimentGroup implements ExperimentGroup {

    private static final ConcurrentCacheSettings DEFAULTS = ConcurrentCacheSettings.builder()
            .cacheName("udqs_plan")
            .keySpaceSize(80_000)
            .hotKeyCount(100)
            .hotKeyRatio(0.7)
            .valueSizeBytes(4_096)
            .computeCostMicros(250)
            .maxEntries(120_000)
            .initialCapacity(2048)
            .loadFactor(0.75)
            .concurrencyLevel(32)
            .ttlSeconds(20)
            .resetCache(false)
            .build();

    private final ConcurrentMapExperimentService service;

    @Override
    public String experimentId() {
        return "udqs_plan";
    }

    @Override
    public String groupId() {
        return "default";
    }

    @Override
    public List<ExperimentOperationDefinition> operations() {
        return List.of(
                ExperimentOperationDefinition.builder()
                        .operationId("lookup_plan")
                        .label("查询路由计划")
                        .hint("热点查询模版 + 租户")
                        .description("Lookup query plan from ConcurrentHashMap cache with TTL and compute cost")
                        .operationType(OperationType.CONTINUOUS_READ)
                        .parameters(sharedParameters())
                        .parameter(ttlParam(DEFAULTS.getTtlSeconds()))
                        .loadShapeTemplate(readShape())
                        .invoker(this::lookupPlan)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("refresh_plan")
                        .label("刷新路由计划")
                        .hint("模拟统计/分片更新导致的计划重建")
                        .description("Refresh a batch of plans, exercising rebuild path and evictions")
                        .operationType(OperationType.CONTINUOUS_WRITE)
                        .parameters(sharedParameters())
                        .parameter(OperationParameter.builder()
                                .name("refreshBatch")
                                .label("刷新批量")
                                .type("number")
                                .required(false)
                                .description("一次刷新多少 key")
                                .example(200)
                                .defaultValue(200)
                                .build())
                        .loadShapeTemplate(writeShape())
                        .invoker(this::refreshPlan)
                        .build()
        );
    }

    private Object lookupPlan(OperationInvocationContext context) {
        ConcurrentCacheRequest request = ConcurrentCacheRequest.from(context, DEFAULTS, "plan-", 500, 200);
        ConcurrentHotCacheEngine.CacheReadResult result = service.read(DEFAULTS.getCacheName(), request);
        return Map.of(
                "key", result.getKey(),
                "source", result.getSource().name(),
                "mapSize", result.getMapSize(),
                "computeMicros", result.getComputeMicros()
        );
    }

    private Object refreshPlan(OperationInvocationContext context) {
        ConcurrentCacheRequest request = ConcurrentCacheRequest.from(context, DEFAULTS, "plan-", 500, 200);
        ConcurrentHotCacheEngine.CacheRefreshResult result = service.refresh(DEFAULTS.getCacheName(), request);
        return Map.of(
                "refreshed", result.getRefreshed(),
                "mapSize", result.getMapSize(),
                "totalMicros", result.getTotalMicros()
        );
    }

    private List<OperationParameter> sharedParameters() {
        return List.of(
                OperationParameter.builder().name("keySpaceSize").label("Key 空间").type("number").required(false)
                        .description("随机 key 范围，决定热点外的总 key 数").example(DEFAULTS.getKeySpaceSize()).defaultValue(DEFAULTS.getKeySpaceSize()).build(),
                OperationParameter.builder().name("hotKeyCount").label("热点 key 数").type("number").required(false)
                        .description("热点 key 数量，配合 hotKeyRatio 控制热点").example(DEFAULTS.getHotKeyCount()).defaultValue(DEFAULTS.getHotKeyCount()).build(),
                OperationParameter.builder().name("hotKeyRatio").label("热点占比").type("number").required(false)
                        .description("热点流量占比，0-0.99").example(DEFAULTS.getHotKeyRatio()).defaultValue(DEFAULTS.getHotKeyRatio()).build(),
                OperationParameter.builder().name("valueSizeBytes").label("Value 大小").type("number").required(false)
                        .description("缓存值体积，模拟路由计划 JSON/Proto").example(DEFAULTS.getValueSizeBytes()).defaultValue(DEFAULTS.getValueSizeBytes()).build(),
                OperationParameter.builder().name("computeCostMicros").label("重建耗时(us)").type("number").required(false)
                        .description("重建查询计划的 CPU 成本").example(DEFAULTS.getComputeCostMicros()).defaultValue(DEFAULTS.getComputeCostMicros()).build(),
                OperationParameter.builder().name("maxEntries").label("最大 entries").type("number").required(false)
                        .description("超过后会随机驱逐，模拟保护策略").example(DEFAULTS.getMaxEntries()).defaultValue(DEFAULTS.getMaxEntries()).build(),
                OperationParameter.builder().name("initialCapacity").label("初始容量").type("number").required(false)
                        .description("ConcurrentHashMap 初始容量").example(DEFAULTS.getInitialCapacity()).defaultValue(DEFAULTS.getInitialCapacity()).build(),
                OperationParameter.builder().name("loadFactor").label("装填因子").type("number").required(false)
                        .description("ConcurrentHashMap 装填因子").example(DEFAULTS.getLoadFactor()).defaultValue(DEFAULTS.getLoadFactor()).build(),
                OperationParameter.builder().name("concurrencyLevel").label("并发级别").type("number").required(false)
                        .description("ConcurrentHashMap 并发级别").example(DEFAULTS.getConcurrencyLevel()).defaultValue(DEFAULTS.getConcurrencyLevel()).build(),
                OperationParameter.builder().name("resetCache").label("重建 Map").type("boolean").required(false)
                        .description("为 true 时按新参数重置 map，清空历史数据").example(false).defaultValue(false).build()
        );
    }

    private OperationParameter ttlParam(int defaultTtl) {
        return OperationParameter.builder()
                .name("ttlSeconds")
                .label("TTL 秒")
                .type("number")
                .required(false)
                .description("缓存 TTL，命中过期触发 rebuild")
                .example(defaultTtl)
                .defaultValue(defaultTtl)
                .build();
    }

    private LoadShapeTemplate readShape() {
        return LoadShapeTemplate.builder()
                .type(LoadShapeType.CONSTANT)
                .qps(3000)
                .concurrency(128)
                .durationSeconds(600L)
                .build();
    }

    private LoadShapeTemplate writeShape() {
        return LoadShapeTemplate.builder()
                .type(LoadShapeType.CONSTANT)
                .qps(80)
                .concurrency(24)
                .durationSeconds(600L)
                .build();
    }
}
