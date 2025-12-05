package com.example.scheduler.loadexecutor.experiment.wallet;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class WalletEvent {
    long userId;
    String eventType;
    Instant eventTime;
    Map<String, Object> payload;
}
