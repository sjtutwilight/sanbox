package com.example.scheduler.loadexecutor.experiment.kafka;

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
public class KafkaKlineExperimentGroup implements ExperimentGroup {

    private final KafkaKlineExperimentService experimentService;

    @Override
    public String experimentId() {
        return "kafka_kline";
    }

    @Override
    public String groupId() {
        return "market-data";
    }

    @Override
    public String groupName() {
        return "行情写入";
    }

    @Override
    public String groupDescription() {
        return "Kafka kline publisher";
    }

    @Override
    public List<ExperimentOperationDefinition> operations() {
        return List.of(
                ExperimentOperationDefinition.builder()
                        .operationId("produce_kline")
                        .label("写入 K 线")
                        .hint("持续向 binance.kline topic 写入指定权重的 symbol 数据")
                        .description("Generate Binance-style kline payloads and publish to Kafka topic binance.kline")
                        .operationType(OperationType.CONTINUOUS_WRITE)
                        .parameters(parameters())
                        .loadShapeTemplate(defaultLoadShape())
                        .invoker(this::handleProduce)
                        .build()
        );
    }

    private List<OperationParameter> parameters() {
        return List.of(
                OperationParameter.builder()
                        .name("topic")
                        .label("Kafka Topic")
                        .type("string")
                        .required(false)
                        .description("目标 Kafka topic，默认 binance.kline")
                        .example("binance.kline")
                        .defaultValue("binance.kline")
                        .build(),
                OperationParameter.builder()
                        .name("symbols")
                        .label("Symbol 权重")
                        .type("string")
                        .required(false)
                        .description("以逗号分隔的 symbol:weight 列表，例如 BTCUSDT:80,ETHUSDT:20")
                        .example("BTCUSDT:80,ETHUSDT:20,SOLUSDT:5")
                        .defaultValue("BTCUSDT:80,ETHUSDT:20")
                        .build(),
                OperationParameter.builder()
                        .name("exchange")
                        .label("交易所标识")
                        .type("string")
                        .required(false)
                        .description("event.exchange 字段，默认 binance")
                        .example("binance")
                        .defaultValue("binance")
                        .build(),
                OperationParameter.builder()
                        .name("interval")
                        .label("时间窗口")
                        .type("string")
                        .required(false)
                        .description("K 线窗口，例如 1m/5m/1h；也可传 intervalSeconds 数值")
                        .example("1m")
                        .defaultValue("1m")
                        .build(),
                OperationParameter.builder()
                        .name("closed")
                        .label("窗口闭合")
                        .type("boolean")
                        .required(false)
                        .description("是否标记 kline.closed=true，默认 true")
                        .example(true)
                        .defaultValue(true)
                        .build()
        );
    }

    private LoadShapeTemplate defaultLoadShape() {
        return LoadShapeTemplate.builder()
                .type(LoadShapeType.CONSTANT)
                .qps(400)
                .concurrency(64)
                .durationSeconds(600L)
                .build();
    }

    private Object handleProduce(OperationInvocationContext context) {
        return experimentService.publish(context);
    }
}
