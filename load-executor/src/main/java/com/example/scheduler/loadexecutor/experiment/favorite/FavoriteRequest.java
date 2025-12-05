package com.example.scheduler.loadexecutor.experiment.favorite;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import lombok.Value;

import java.time.Duration;
import java.util.Map;

@Value
public class FavoriteRequest {
    long userId;
    String symbol;
    String tags;
    Duration ttlOverride;

    public static FavoriteRequest from(OperationInvocationContext context) {
        Map<String, Object> payload = context.getPayload();
        if (payload == null) {
            throw new IllegalArgumentException("favorite payload is required");
        }
        Object userIdRaw = payload.get("userId");
        if (!(userIdRaw instanceof Number number) || number.longValue() <= 0) {
            throw new IllegalArgumentException("userId must be a positive number");
        }
        String symbol = payload.containsKey("symbol") ? String.valueOf(payload.get("symbol")) : null;
        String tags = payload.containsKey("tags") ? String.valueOf(payload.get("tags")) : null;
        Duration ttl = null;
        Object ttlRaw = payload.get("ttlSeconds");
        if (ttlRaw instanceof Number ttlNumber) {
            ttl = Duration.ofSeconds(Math.max(1, ttlNumber.longValue()));
        } else if (ttlRaw instanceof String ttlString && !ttlString.isBlank()) {
            ttl = Duration.ofSeconds(Math.max(1, Long.parseLong(ttlString)));
        }
        return new FavoriteRequest(number.longValue(), symbol, tags, ttl);
    }
}
