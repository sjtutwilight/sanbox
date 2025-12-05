package com.example.scheduler.loadexecutor.experiment.wallet;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.Map;

@Value
@Builder
public class WalletWarmRequest {
    long startUserId;
    int userCount;
    int batchSize;
    Duration ttlOverride;

    public static WalletWarmRequest from(OperationInvocationContext context) {
        Map<String, Object> payload = context.getPayload() != null ? context.getPayload() : Map.of();
        Map<String, Object> range = WalletRequestSupport.mapValue(payload.get("userRange"));
        long startUserId = WalletRequestSupport.longValue(range.getOrDefault("start", payload.get("startUserId")), 100000L);
        int userCount = WalletRequestSupport.intValue(range.getOrDefault("count", payload.get("userCount")), 5000);
        int batchSize = WalletRequestSupport.intValue(payload.get("batchSize"), 500);
        Duration ttl = payload.containsKey("ttlSeconds")
                ? WalletRequestSupport.durationValue(payload.get("ttlSeconds"), null)
                : null;
        return WalletWarmRequest.builder()
                .startUserId(startUserId)
                .userCount(Math.max(1, userCount))
                .batchSize(Math.max(1, batchSize))
                .ttlOverride(ttl)
                .build();
    }
}
