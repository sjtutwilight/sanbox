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

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MetadataCacheExperimentGroup implements ExperimentGroup {

    private static final ConcurrentCacheSettings DEFAULTS = ConcurrentCacheSettings.builder()
            .cacheName("metadata_catalog")
            .keySpaceSize(100_000)
            .hotKeyCount(200)
            .hotKeyRatio(0.7)
            .valueSizeBytes(2_048)
            .computeCostMicros(400)
            .maxEntries(200_000)
            .initialCapacity(1024)
            .loadFactor(0.75)
            .concurrencyLevel(16)
            .ttlSeconds(60)
            .resetCache(false)
            .build();

    private final ConcurrentMapExperimentService service;

    @Override
    public String experimentId() {
        return "metadata_cache";
    }

    @Override
    public String groupId() {
        return "default";
    }

    @Override
    public List<ExperimentOperationDefinition> operations() {
        return List.of(
                ExperimentOperationDefinition.builder()
                        .operationId("read_catalog")
                        .label("读元数据缓存")
                        .hint("模拟元数据/权限读取，热点访问为主")
                        .description("ConcurrentHashMap cache-aside for metadata catalog with compute cost and TTL")
                        .operationType(OperationType.CONTINUOUS_READ)
                        .parameters(sharedParameters())
                        .parameter(ttlParam(DEFAULTS.getTtlSeconds()))
                        .loadShapeTemplate(readShape())
                        .invoker(this::readCatalog)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("invalidate_catalog")
                        .label("失效元数据缓存")
                        .hint("模拟 schema 发布或权限变更的批量失效")
                        .description("Invalidate a range of catalog entries to trigger rebuild under read load")
                        .operationType(OperationType.CONTINUOUS_WRITE)
                        .parameters(sharedParameters())
                        .parameter(OperationParameter.builder()
                                .name("invalidateRange")
                                .label("失效数量")
                                .type("number")
                                .required(false)
                                .description("一次失效的 key 数量")
                                .example(500)
                                .defaultValue(500)
                                .build())
                        .loadShapeTemplate(writeShape())
                        .invoker(this::invalidateCatalog)
                        .build()
        );
    }

    private Object readCatalog(OperationInvocationContext context) {
        ConcurrentCacheRequest request = ConcurrentCacheRequest.from(context, DEFAULTS, "catalog-", 500, 200);
        ConcurrentHotCacheEngine.CacheReadResult result = service.read(DEFAULTS.getCacheName(), request);
        return Map.of(
                "key", result.getKey(),
                "source", result.getSource().name(),
                "mapSize", result.getMapSize(),
                "computeMicros", result.getComputeMicros()
        );
    }

    private Object invalidateCatalog(OperationInvocationContext context) {
        ConcurrentCacheRequest request = ConcurrentCacheRequest.from(context, DEFAULTS, "catalog-", 500, 200);
        ConcurrentHotCacheEngine.CacheInvalidateResult result = service.invalidate(DEFAULTS.getCacheName(), request);
        return Map.of(
                "requested", result.getRequested(),
                "removed", result.getRemoved(),
                "remainingSize", result.getRemainingSize()
        );
    }

    private List<OperationParameter> sharedParameters() {
        return List.of(
                OperationParameter.builder().name("keySpaceSize").label("Key 空间").type("number").required(false)
                        .description("随机 key 范围，决定热点外的总 key 数").example(100000).defaultValue(DEFAULTS.getKeySpaceSize()).build(),
                OperationParameter.builder().name("hotKeyCount").label("热点 key 数").type("number").required(false)
                        .description("热点 key 数量，配合 hotKeyRatio 控制热点").example(200).defaultValue(DEFAULTS.getHotKeyCount()).build(),
                OperationParameter.builder().name("hotKeyRatio").label("热点占比").type("number").required(false)
                        .description("热点流量占比，0-0.99").example(0.7).defaultValue(DEFAULTS.getHotKeyRatio()).build(),
                OperationParameter.builder().name("valueSizeBytes").label("Value 大小").type("number").required(false)
                        .description("缓存值体积，模拟大对象或 JSON").example(2048).defaultValue(DEFAULTS.getValueSizeBytes()).build(),
                OperationParameter.builder().name("computeCostMicros").label("重建耗时(us)").type("number").required(false)
                        .description("computeIfAbsent 计算耗时，放大锁竞争").example(400).defaultValue(DEFAULTS.getComputeCostMicros()).build(),
                OperationParameter.builder().name("maxEntries").label("最大 entries").type("number").required(false)
                        .description("超过后会随机驱逐，模拟 OOM/膨胀前的保护").example(200000).defaultValue(DEFAULTS.getMaxEntries()).build(),
                OperationParameter.builder().name("initialCapacity").label("初始容量").type("number").required(false)
                        .description("ConcurrentHashMap 初始容量").example(1024).defaultValue(DEFAULTS.getInitialCapacity()).build(),
                OperationParameter.builder().name("loadFactor").label("装填因子").type("number").required(false)
                        .description("ConcurrentHashMap 装填因子").example(0.75).defaultValue(DEFAULTS.getLoadFactor()).build(),
                OperationParameter.builder().name("concurrencyLevel").label("并发级别").type("number").required(false)
                        .description("ConcurrentHashMap 并发级别").example(16).defaultValue(DEFAULTS.getConcurrencyLevel()).build(),
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
                .qps(2000)
                .concurrency(64)
                .durationSeconds(600L)
                .build();
    }

    private LoadShapeTemplate writeShape() {
        return LoadShapeTemplate.builder()
                .type(LoadShapeType.CONSTANT)
                .qps(200)
                .concurrency(16)
                .durationSeconds(300L)
                .build();
    }
}
