package com.example.scheduler.loadexecutor.experiment.onchain;

import com.example.scheduler.experiment.OperationType;
import com.example.scheduler.loadexecutor.datagenerator.onchain.OnchainMockDataGeneratorRequest;
import com.example.scheduler.loadexecutor.datagenerator.onchain.OnchainMockDataGeneratorService;
import com.example.scheduler.loadexecutor.experiment.ExperimentGroup;
import com.example.scheduler.loadexecutor.experiment.ExperimentOperationDefinition;
import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import com.example.scheduler.loadexecutor.experiment.OperationParameter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.example.scheduler.loadexecutor.experiment.support.RequestSupport.boolValue;
import static com.example.scheduler.loadexecutor.experiment.support.RequestSupport.intValue;

@Component
@RequiredArgsConstructor
public class OnchainMockExperimentGroup implements ExperimentGroup {

    private final OnchainMockDataGeneratorService generatorService;

    @Override
    public String experimentId() {
        return "onchain_mock";
    }

    @Override
    public String experimentName() {
        return "Onchain Mock DWD";
    }

    @Override
    public String groupId() {
        return "mock_dwd";
    }

    @Override
    public String groupName() {
        return "Mock DWD Pipeline";
    }

    @Override
    public String groupDescription() {
        return "一键初始化 dim / redis / ods 并生成 token price，供 dex_swap_dwd_job 验证";
    }

    @Override
    public List<ExperimentOperationDefinition> operations() {
        return List.of(
                ExperimentOperationDefinition.builder()
                        .operationId("seed_mock")
                        .label("一键造数")
                        .hint("初始化 dim_token/dim_pool/dim_account、写入 Redis 标签和 ODS 事实")
                        .description("生成 onchain mock 数据（tx/receipt/swap/price）并落表，供 Flink DWD job 使用")
                        .operationType(OperationType.INIT_DATA)
                        .invoker(this::invokeSeed)
                        .parameter(boolParameter("initMetadata", "初始化维表", true, "写入 dim_token/dim_dex_pool/dim_account"))
                        .parameter(boolParameter("refreshAccountTags", "写 Redis 标签", true, "刷新 dim_account_tag_latest 缓存"))
                        .parameter(boolParameter("produceTokenPrices", "写 token price", true, "mock dim_token_price_current upsert 流"))
                        .parameter(intParameter("priceUpdateCycles", "价格更新次数", 12, "token price mock 循环次数"))
                        .parameter(intParameter("swapsPerChain", "每链 swap 条数", 120, "每个 chain 生成的 swap/tx/receipt 数"))
                        .parameter(boolParameter("includeTransactions", "写 tx", true, "ODS tx topic 是否写入"))
                        .parameter(boolParameter("includeReceipts", "写 receipt", true, "ODS receipt topic 是否写入"))
                        .parameter(intParameter("emitDelayMillis", "事件发送间隔(ms)", 50, "相邻 swap 事件的节流毫秒数"))
                        .parameter(intParameter("tagAccountTarget", "Tag account 数量", 100000, "每条链要维护的标签账户个数"))
                        .parameter(intParameter("tagUpdatesPerSecond", "每秒标签更新数", 100, "每秒随机挑选多少个账户刷新标签"))
                        .parameter(OperationParameter.builder()
                                .name("chainIds")
                                .label("链 ID 列表")
                                .type("string")
                                .description("逗号分隔链 ID，例如 1,42161")
                                .defaultValue("1,42161")
                                .example("1,42161")
                                .build())
                        .build()
        );
    }

    private Object invokeSeed(OperationInvocationContext context) {
        Map<String, Object> payload = context.getPayload();
        OnchainMockDataGeneratorRequest request = new OnchainMockDataGeneratorRequest();
        request.setChainIds(resolveChainIds(payload.get("chainIds")));
        request.setSwapsPerChain(intValue(payload.get("swapsPerChain"), request.getSwapsPerChain()));
        request.setInitMetadata(boolValue(payload.get("initMetadata"), request.isInitMetadata()));
        request.setRefreshAccountTags(boolValue(payload.get("refreshAccountTags"), request.isRefreshAccountTags()));
        request.setProduceTokenPrices(boolValue(payload.get("produceTokenPrices"), request.isProduceTokenPrices()));
        request.setPriceUpdateCycles(intValue(payload.get("priceUpdateCycles"), request.getPriceUpdateCycles()));
        request.setIncludeTransactions(boolValue(payload.get("includeTransactions"), request.isIncludeTransactions()));
        request.setIncludeReceipts(boolValue(payload.get("includeReceipts"), request.isIncludeReceipts()));
        request.setEmitDelayMillis(intValue(payload.get("emitDelayMillis"), (int) request.getEmitDelayMillis()));
        request.setTagAccountTarget(intValue(payload.get("tagAccountTarget"), request.getTagAccountTarget()));
        request.setTagUpdatesPerSecond(intValue(payload.get("tagUpdatesPerSecond"), request.getTagUpdatesPerSecond()));
        return generatorService.generate(request);
    }

    private OperationParameter boolParameter(String name, String label, boolean defaultValue, String description) {
        return OperationParameter.builder()
                .name(name)
                .label(label)
                .type("boolean")
                .defaultValue(defaultValue)
                .description(description)
                .build();
    }

    private OperationParameter intParameter(String name, String label, int defaultValue, String description) {
        return OperationParameter.builder()
                .name(name)
                .label(label)
                .type("int")
                .defaultValue(defaultValue)
                .description(description)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Integer> resolveChainIds(Object value) {
        List<Integer> defaults = List.of(1, 42161);
        if (value == null) {
            return defaults;
        }
        if (value instanceof Collection<?> collection) {
            List<Integer> result = new ArrayList<>();
            for (Object item : collection) {
                Integer parsed = parseChainId(item);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
            return result.isEmpty() ? defaults : result;
        }
        if (value instanceof String s && !s.isBlank()) {
            String[] parts = s.split(",");
            List<Integer> result = new ArrayList<>();
            for (String part : parts) {
                Integer parsed = parseChainId(part.trim());
                if (parsed != null) {
                    result.add(parsed);
                }
            }
            return result.isEmpty() ? defaults : result;
        }
        Integer single = parseChainId(value);
        return single != null ? List.of(single) : defaults;
    }

    private Integer parseChainId(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
