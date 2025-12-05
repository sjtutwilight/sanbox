package com.example.scheduler.loadexecutor.experiment.kafka;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KlineEvent {
    long eventTime;
    String exchange;
    long ingestTime;
    String interval;
    String symbol;
    Window kline;

    @Value
    @Builder
    public static class Window {
        String baseVolume;
        String quoteVolume;
        String openPrice;
        String closePrice;
        String highPrice;
        String lowPrice;
        long startTime;
        long closeTime;
        boolean closed;
        long tradeCount;
    }
}
