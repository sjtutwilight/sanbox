package com.example.scheduler.loadexecutor.datagenerator.onchain;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Set;

@Value
@Builder
public class OnchainMockDataGeneratorResponse {
    Set<Integer> chainIds;
    int tokensWritten;
    int poolsWritten;
    int accountsWritten;
    int tagsWritten;
    int txEvents;
    int receiptEvents;
    int swapEvents;
    int priceEvents;
    Instant generatedAt;
}
