package com.example.scheduler.loadexecutor.experiment.wallet;

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

@Component
@RequiredArgsConstructor
public class WalletQueryExperimentGroup implements ExperimentGroup {

    private final WalletQueryExperimentService experimentService;

    @Override
    public String experimentId() {
        return "wallet_query";
    }

    @Override
    public String groupId() {
        return "default";
    }

    @Override
    public List<ExperimentOperationDefinition> operations() {
        return List.of(
                ExperimentOperationDefinition.builder()
                        .operationId("query_snapshot")
                        .label("查询钱包快照")
                        .hint("模拟 /wallet 的统一资产查询")
                        .description("Read wallet snapshot via cache-aside strategy with optional history/risk enrichments")
                        .operationType(OperationType.CONTINUOUS_READ)
                        .parameters(queryParameters())
                        .loadShapeTemplate(LoadShapeTemplate.builder()
                                .type(LoadShapeType.CONSTANT)
                                .qps(1500)
                                .concurrency(80)
                                .durationSeconds(1800L)
                                .build())
                        .invoker(this::handleQuerySnapshot)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("warm_snapshot")
                        .label("预热钱包快照")
                        .hint("批量回源 + 写缓存")
                        .description("Warm up wallet snapshots for a range of users")
                        .operationType(OperationType.INIT_REDIS)
                        .parameters(rangeParameters())
                        .parameter(OperationParameter.builder()
                                .name("batchSize")
                                .label("批处理大小")
                                .type("number")
                                .required(false)
                                .description("每批处理多少用户后让出线程")
                                .example(500)
                                .defaultValue(500)
                                .build())
                        .parameter(OperationParameter.builder()
                                .name("ttlSeconds")
                                .label("缓存 TTL")
                                .type("number")
                                .required(false)
                                .description("缓存快照的 TTL")
                                .example(600)
                                .defaultValue(600)
                                .build())
                        .loadShapeTemplate(LoadShapeTemplate.builder()
                                .type(LoadShapeType.RAMP)
                                .qps(200)
                                .concurrency(16)
                                .durationSeconds(1200L)
                                .build())
                        .invoker(this::handleWarm)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("rebuild_ledger")
                        .label("重建快照")
                        .hint("从流水重放资产快照")
                        .description("Replay historical ledger to rebuild wallet snapshots")
                        .operationType(OperationType.INIT_DATA)
                        .parameters(rangeParameters())
                        .parameter(OperationParameter.builder()
                                .name("daysBack")
                                .label("回放天数")
                                .type("number")
                                .required(false)
                                .description("回放多少天的流水")
                                .example(30)
                                .defaultValue(7)
                                .build())
                        .parameter(OperationParameter.builder()
                                .name("batchSize")
                                .label("批处理大小")
                                .type("number")
                                .required(false)
                                .description("多少用户作为一批处理")
                                .example(200)
                                .defaultValue(200)
                                .build())
                        .parameter(OperationParameter.builder()
                                .name("retryMode")
                                .label("重试模式")
                                .type("string")
                                .required(false)
                                .description("at_most_once / at_least_once")
                                .example("at_least_once")
                                .defaultValue("at_most_once")
                                .build())
                        .loadShapeTemplate(LoadShapeTemplate.builder()
                                .type(LoadShapeType.CONSTANT)
                                .qps(300)
                                .concurrency(24)
                                .durationSeconds(2400L)
                                .build())
                        .invoker(this::handleRebuild)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("publish_bus")
                        .label("发布资产事件")
                        .hint("Kafka 事件总线写入")
                        .description("Publish wallet update events to Kafka with configurable payload sizes")
                        .operationType(OperationType.CONTINUOUS_WRITE)
                        .parameter(OperationParameter.builder()
                                .name("topic")
                                .label("Kafka Topic")
                                .type("string")
                                .required(false)
                                .description("Kafka topic name")
                                .example("wallet.events")
                                .defaultValue("wallet.events")
                                .build())
                        .parameter(OperationParameter.builder()
                                .name("payloadSize")
                                .label("Payload 大小")
                                .type("number")
                                .required(false)
                                .description("序列化后近似大小，单位字节")
                                .example(2048)
                                .defaultValue(1024)
                                .build())
                        .parameter(OperationParameter.builder()
                                .name("partitionSkew")
                                .label("分区倾斜")
                                .type("number")
                                .required(false)
                                .description("高倾斜模拟 0.9/0.95")
                                .example(0.95)
                                .defaultValue(0.9)
                                .build())
                        .loadShapeTemplate(LoadShapeTemplate.builder()
                                .type(LoadShapeType.CONSTANT)
                                .qps(800)
                                .concurrency(48)
                                .durationSeconds(1800L)
                                .build())
                        .invoker(this::handlePublish)
                        .build()
        );
    }

    private Object handleQuerySnapshot(OperationInvocationContext context) {
        return experimentService.querySnapshot(context);
    }

    private Object handleWarm(OperationInvocationContext context) {
        return experimentService.warmSnapshots(context);
    }

    private Object handleRebuild(OperationInvocationContext context) {
        return experimentService.rebuildLedger(context);
    }

    private Object handlePublish(OperationInvocationContext context) {
        return experimentService.publishBus(context);
    }

    private List<OperationParameter> rangeParameters() {
        return List.of(
                OperationParameter.builder()
                        .name("startUserId")
                        .label("起始用户 ID")
                        .type("number")
                        .required(false)
                        .description("用于生成 userId 的起点")
                        .example(100000)
                        .defaultValue(100000)
                        .build(),
                OperationParameter.builder()
                        .name("userCount")
                        .label("用户范围")
                        .type("number")
                        .required(false)
                        .description("参与操作的用户数量")
                        .example(5000)
                        .defaultValue(5000)
                        .build()
        );
    }

    private List<OperationParameter> queryParameters() {
        return List.of(
                OperationParameter.builder()
                        .name("userSegment")
                        .label("用户分段")
                        .type("string")
                        .required(false)
                        .description("hot/vip/institution/mixed")
                        .example("vip")
                        .defaultValue("mixed")
                        .build(),
                OperationParameter.builder()
                        .name("assetCount")
                        .label("资产数量")
                        .type("number")
                        .required(false)
                        .description("快照返回的资产条目数")
                        .example(120)
                        .defaultValue(40)
                        .build(),
                OperationParameter.builder()
                        .name("includeHistory")
                        .label("是否包含历史流水")
                        .type("boolean")
                        .required(false)
                        .description("true 则附带最近 N 天流水")
                        .example(true)
                        .defaultValue(false)
                        .build(),
                OperationParameter.builder()
                        .name("historyDays")
                        .label("历史天数")
                        .type("number")
                        .required(false)
                        .description("includeHistory=true 时生效")
                        .example(7)
                        .defaultValue(3)
                        .build(),
                OperationParameter.builder()
                        .name("includeRisk")
                        .label("是否包含风控指标")
                        .type("boolean")
                        .required(false)
                        .description("VIP 用户通常需要")
                        .example(true)
                        .defaultValue(false)
                        .build(),
                OperationParameter.builder()
                        .name("retainSnapshots")
                        .label("保留快照引用")
                        .type("boolean")
                        .required(false)
                        .description("开启后在内存中额外保留快照，便于复现长引用链")
                        .example(true)
                        .defaultValue(false)
                        .build(),
                OperationParameter.builder()
                        .name("retainPerUser")
                        .label("每用户保留数量")
                        .type("number")
                        .required(false)
                        .description("retainSnapshots=true 时生效，控制每个用户保留的快照数量")
                        .example(50)
                        .defaultValue(0)
                        .build(),
                OperationParameter.builder()
                        .name("retainGlobal")
                        .label("全局保留数量")
                        .type("number")
                        .required(false)
                        .description(">0 表示限制大小，<0 表示无限保留，默认0不启用")
                        .example(20000)
                        .defaultValue(0)
                        .build(),
                OperationParameter.builder()
                        .name("fillerBytes")
                        .label("额外占用字节")
                        .type("number")
                        .required(false)
                        .description("每个快照附加的随机字节大小，用于放大内存压力")
                        .example(65536)
                        .defaultValue(0)
                        .build(),
                OperationParameter.builder()
                        .name("ttlSeconds")
                        .label("缓存 TTL")
                        .type("number")
                        .required(false)
                        .description("覆盖默认 TTL")
                        .example(30)
                        .defaultValue(30)
                        .build()
        );
    }
}
