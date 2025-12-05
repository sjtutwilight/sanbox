package com.example.scheduler.loadexecutor.experiment.favorite;

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
public class FavoriteExperimentGroup implements ExperimentGroup {

    private final FavoriteExperimentService experimentService;

    @Override
    public String experimentId() {
        return "favorite";
    }

    @Override
    public String groupId() {
        return "default";
    }

    @Override
    public List<ExperimentOperationDefinition> operations() {
        return List.of(
                ExperimentOperationDefinition.builder()
                        .operationId("read_cache_aside")
                        .label("缓存读取")
                        .hint("在用户区间内随机挑选用户执行 read")
                        .description("Read favorite symbols via cache-aside strategy")
                        .operationType(OperationType.CONTINUOUS_READ)
                        .parameters(rangeParameters())
                        .parameter(OperationParameter.builder()
                                .name("ttlSeconds")
                                .label("缓存 TTL")
                                .type("number")
                                .required(false)
                                .description("可选覆盖缓存 TTL；不填沿用系统默认")
                                .example(30)
                                .defaultValue(null)
                                .build())
                        .loadShapeTemplate(readLoadShapeTemplate())
                        .invoker(this::handleReadCacheAside)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("add_favorite")
                        .label("添加收藏")
                        .hint("在用户区间内随机挑选用户执行写入")
                        .description("Add/Upsert favorite symbol and invalidate cache")
                        .operationType(OperationType.CONTINUOUS_WRITE)
                        .parameters(rangeParameters())
                        .parameter(OperationParameter.builder()
                                .name("symbol")
                                .label("候选交易对")
                                .type("string")
                                .required(false)
                                .description("以逗号分隔的交易对集合，随机挑选")
                                .example("BTCUSDT,ETHUSDT,SOLUSDT")
                                .defaultValue("BTCUSDT,ETHUSDT,SOLUSDT")
                                .build())
                        .parameter(OperationParameter.builder()
                                .name("tags")
                                .label("标签")
                                .type("string")
                                .required(false)
                                .description("写入 favorite_symbol 时附带的标签")
                                .example("scene1")
                                .defaultValue("generated")
                                .build())
                        .loadShapeTemplate(writeLoadShapeTemplate())
                        .invoker(this::handleAddFavorite)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("remove_favorite")
                        .label("移除收藏")
                        .hint("随机挑选用户并移除随机交易对")
                        .description("Remove favorite symbol and invalidate cache")
                        .operationType(OperationType.CONTINUOUS_WRITE)
                        .parameters(rangeParameters())
                        .parameter(OperationParameter.builder()
                                .name("symbol")
                                .label("候选交易对")
                                .type("string")
                                .required(false)
                                .description("以逗号分隔的交易对集合，随机选择移除")
                                .example("BTCUSDT,ETHUSDT,SOLUSDT")
                                .defaultValue("BTCUSDT,ETHUSDT,SOLUSDT")
                                .build())
                        .loadShapeTemplate(writeLoadShapeTemplate())
                        .invoker(this::handleRemoveFavorite)
                        .build(),
                ExperimentOperationDefinition.builder()
                        .operationId("warm_cache")
                        .label("缓存预热")
                        .hint("将 MySQL 数据批量写入缓存，不对外提供读流量")
                        .description("Load user favorites from MySQL and write cache without serving traffic")
                        .operationType(OperationType.INIT_REDIS)
                        .parameters(rangeParameters())
                        .parameter(OperationParameter.builder()
                                .name("ttlSeconds")
                                .label("缓存 TTL")
                                .type("number")
                                .required(false)
                                .description("预热时写入的 TTL；为空则采用系统默认")
                                .example(60)
                                .defaultValue(60)
                                .build())
                        .loadShapeTemplate(warmLoadShapeTemplate())
                        .invoker(this::handleWarmCache)
                        .build()
        );
    }

    private Object handleReadCacheAside(OperationInvocationContext context) {
        FavoriteRequest request = FavoriteRequest.from(context);
        FavoriteReadResult result = experimentService.readWithCache(request.getUserId(), request.getTtlOverride());
        return Map.of(
                "userId", result.getUserId(),
                "source", result.getSource().name(),
                "symbols", result.getSymbols()
        );
    }

    private Object handleAddFavorite(OperationInvocationContext context) {
        FavoriteRequest request = FavoriteRequest.from(context);
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required for add_favorite");
        }
        experimentService.addFavorite(request.getUserId(), request.getSymbol(), request.getTags());
        return Map.of(
                "status", "OK",
                "action", "ADD",
                "userId", request.getUserId(),
                "symbol", request.getSymbol()
        );
    }

    private Object handleRemoveFavorite(OperationInvocationContext context) {
        FavoriteRequest request = FavoriteRequest.from(context);
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required for remove_favorite");
        }
        experimentService.removeFavorite(request.getUserId(), request.getSymbol());
        return Map.of(
                "status", "OK",
                "action", "REMOVE",
                "userId", request.getUserId(),
                "symbol", request.getSymbol()
        );
    }

    private Object handleWarmCache(OperationInvocationContext context) {
        FavoriteRequest request = FavoriteRequest.from(context);
        Duration ttl = request.getTtlOverride();
        experimentService.warmUpCache(request.getUserId(), ttl);
        return Map.of(
                "status", "OK",
                "action", "WARM",
                "userId", request.getUserId(),
                "ttlSeconds", ttl != null ? ttl.toSeconds() : null
        );
    }

    private List<OperationParameter> rangeParameters() {
        return List.of(
                OperationParameter.builder()
                        .name("startUserId")
                        .label("起始用户ID")
                        .type("number")
                        .required(true)
                        .description("随机用户基准起点")
                        .example(100000)
                        .defaultValue(100000)
                        .build(),
                OperationParameter.builder()
                        .name("userCount")
                        .label("用户范围")
                        .type("number")
                        .required(false)
                        .description("从起始用户起，参与负载的用户数量")
                        .example(5000)
                        .defaultValue(1000)
                        .build()
        );
    }

    private LoadShapeTemplate readLoadShapeTemplate() {
        return LoadShapeTemplate.builder()
                .type(LoadShapeType.CONSTANT)
                .qps(800)
                .concurrency(32)
                .durationSeconds(300L)
                .build();
    }

    private LoadShapeTemplate writeLoadShapeTemplate() {
        return LoadShapeTemplate.builder()
                .type(LoadShapeType.CONSTANT)
                .qps(500)
                .concurrency(16)
                .durationSeconds(300L)
                .build();
    }

    private LoadShapeTemplate warmLoadShapeTemplate() {
        return LoadShapeTemplate.builder()
                .type(LoadShapeType.RAMP)
                .qps(200)
                .concurrency(8)
                .durationSeconds(600L)
                .build();
    }
}
