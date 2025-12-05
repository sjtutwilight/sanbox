package com.example.scheduler.loadexecutor.experiment.wallet;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class WalletRebuildRequest {
    long startUserId;
    int userCount;
    int batchSize;
    int daysBack;
    String retryMode;

    public static WalletRebuildRequest from(OperationInvocationContext context) {
        Map<String, Object> payload = context.getPayload() != null ? context.getPayload() : Map.of();
        Map<String, Object> range = WalletRequestSupport.mapValue(payload.get("userRange"));
        long start = WalletRequestSupport.longValue(range.getOrDefault("start", payload.get("startUserId")), 100000L);
        int count = WalletRequestSupport.intValue(range.getOrDefault("count", payload.get("userCount")), 1000);
        int batch = WalletRequestSupport.intValue(payload.get("batchSize"), 200);
        int daysBack = WalletRequestSupport.intValue(payload.get("daysBack"), 7);
        String retryMode = payload.getOrDefault("retryMode", "at_most_once").toString();
        return WalletRebuildRequest.builder()
                .startUserId(start)
                .userCount(Math.max(1, count))
                .batchSize(Math.max(1, batch))
                .daysBack(Math.max(1, daysBack))
                .retryMode(retryMode)
                .build();
    }
}
