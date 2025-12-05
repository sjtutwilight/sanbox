package com.example.scheduler.loadexecutor.experiment.wallet;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Value
@Builder
public class WalletPublishRequest {
    String topic;
    long userId;
    int payloadSize;
    double partitionSkew;

    public static WalletPublishRequest from(OperationInvocationContext context) {
        Map<String, Object> payload = context.getPayload() != null ? context.getPayload() : Map.of();
        String topic = payload.getOrDefault("topic", "wallet.events").toString();
        int payloadSize = WalletRequestSupport.intValue(payload.get("payloadSize"), 1024);
        double skew = Math.min(0.99, Math.max(0, WalletRequestSupport.doubleValue(payload.get("partitionSkew"), 0.9)));
        long startUserId = WalletRequestSupport.longValue(payload.get("startUserId"), 100000L);
        int userCount = WalletRequestSupport.intValue(payload.get("userCount"), 10000);
        long userId = startUserId + (context.getSequence() % Math.max(1, userCount));
        if (ThreadLocalRandom.current().nextDouble() < skew) {
            userId = startUserId;
        }
        return WalletPublishRequest.builder()
                .topic(topic)
                .userId(userId)
                .payloadSize(Math.max(128, payloadSize))
                .partitionSkew(skew)
                .build();
    }
}
