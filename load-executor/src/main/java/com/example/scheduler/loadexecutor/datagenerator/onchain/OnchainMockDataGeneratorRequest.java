package com.example.scheduler.loadexecutor.datagenerator.onchain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OnchainMockDataGeneratorRequest {

    @NotEmpty
    private List<Integer> chainIds = new ArrayList<>(List.of(1, 42161));

    @Min(1)
    @Max(10000)
    private int swapsPerChain = 100;

    private boolean initMetadata = true;
    private boolean refreshAccountTags = true;
    private boolean produceTokenPrices = true;

    @Min(1)
    @Max(10000)
    private int priceUpdateCycles = 60;  // 默认持续产出60秒,用于观测Flink Job

    private boolean includeTransactions = true;
    private boolean includeReceipts = true;

    @Min(0)
    @Max(60000)
    private long emitDelayMillis = 50;

    @Min(1)
    @Max(1000000)
    private int tagAccountTarget = 100000;

    @Min(0)
    @Max(10000)
    private int tagUpdatesPerSecond = 100;
}
