package com.example.scheduler.loadexecutor.experiment.wallet;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Value
@Builder
public class WalletQueryRequest {
    long userId;
    String userSegment;
    int assetCount;
    boolean includeHistory;
    int historyDays;
    boolean includeRisk;
    Duration ttlOverride;
    boolean retainSnapshots;
    int retainPerUser;
    int retainGlobal;
    int fillerBytes;

    public int segmentOffset() {
        return switch (userSegment.toLowerCase()) {
            case "vip" -> 5;
            case "institution" -> 8;
            case "hot" -> 0;
            case "cold" -> 2;
            default -> 1;
        };
    }

    public static WalletQueryRequest from(OperationInvocationContext context) {
        Map<String, Object> payload = context.getPayload() != null ? context.getPayload() : Map.of();
        String segment = payload.getOrDefault("userSegment", "mixed").toString();
        long baseStart = WalletRequestSupport.longValue(payload.get("startUserId"), 100000L);
        int userCount = WalletRequestSupport.intValue(payload.get("userCount"), 5000);
        long userId = pickUserId(segment, baseStart, userCount, context.getSequence());

        int assetCount = WalletRequestSupport.intValue(payload.get("assetCount"), 40);
        boolean includeHistory = WalletRequestSupport.boolValue(payload.get("includeHistory"), false);
        int historyDays = WalletRequestSupport.intValue(payload.get("historyDays"), includeHistory ? 7 : 0);
        boolean includeRisk = WalletRequestSupport.boolValue(payload.get("includeRisk"), false);
        boolean retainSnapshots = WalletRequestSupport.boolValue(payload.get("retainSnapshots"), false);
        int retainPerUser = WalletRequestSupport.intValue(payload.get("retainPerUser"), retainSnapshots ? 20 : 0);
        int retainGlobal = WalletRequestSupport.intValue(payload.get("retainGlobal"), 0);
        int fillerBytes = WalletRequestSupport.intValue(payload.get("fillerBytes"), 0);
        Duration ttlOverride = payload.containsKey("ttlSeconds")
                ? WalletRequestSupport.durationValue(payload.get("ttlSeconds"), null)
                : null;
        return WalletQueryRequest.builder()
                .userId(userId)
                .userSegment(segment)
                .assetCount(assetCount)
                .includeHistory(includeHistory)
                .historyDays(historyDays)
                .includeRisk(includeRisk)
                .ttlOverride(ttlOverride)
                .retainSnapshots(retainSnapshots)
                .retainPerUser(Math.max(0, retainPerUser))
                .retainGlobal(retainGlobal)
                .fillerBytes(Math.max(0, fillerBytes))
                .build();
    }

    private static long pickUserId(String segment, long baseStart, int userCount, long sequence) {
        long offset;
        if ("vip".equalsIgnoreCase(segment)) {
            offset = ThreadLocalRandom.current().nextInt(1000);
            baseStart = 900000;
        } else if ("hot".equalsIgnoreCase(segment)) {
            offset = sequence % Math.max(1, Math.min(2000, userCount));
        } else if ("institution".equalsIgnoreCase(segment)) {
            baseStart = 800000;
            offset = sequence % Math.max(1, userCount / 2 + 1);
        } else {
            offset = sequence % Math.max(1, userCount);
        }
        return baseStart + offset;
    }
}
