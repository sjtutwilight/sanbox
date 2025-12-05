package com.example.scheduler.loadexecutor.experiment.kafka;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaKlineExperimentService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> publish(OperationInvocationContext context) {
        KafkaKlineRequest request = KafkaKlineRequest.from(context);
        KafkaKlineRequest.SymbolConfig symbolConfig = request.pickSymbol();
        KlineEvent event = buildEvent(request, symbolConfig);
        String payload = toJson(event);
        send(request.getTopic(), symbolConfig.getSymbol(), payload);
        return Map.of(
                "status", "SENT",
                "topic", request.getTopic(),
                "symbol", symbolConfig.getSymbol(),
                "eventTime", event.getEventTime(),
                "sequence", context.getSequence(),
                "payloadBytes", payload.length()
        );
    }

    private KlineEvent buildEvent(KafkaKlineRequest request, KafkaKlineRequest.SymbolConfig symbol) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        Duration interval = request.getInterval();
        long intervalMillis = Math.max(1000L, interval.toMillis());
        long startTime = now - (now % intervalMillis);
        long closeTime = startTime + intervalMillis - 1;

        double openPrice = priceWithVariance(random, symbol.getBasePrice(), symbol.getPriceVariance());
        double closePrice = priceWithVariance(random, openPrice, symbol.getPriceVariance());
        double highPrice = Math.max(openPrice, closePrice) * (1 + random.nextDouble(symbol.getPriceVariance()));
        double lowPrice = Math.min(openPrice, closePrice) * (1 - random.nextDouble(symbol.getPriceVariance()));
        double baseVolume = magnitudeWithVariance(random, symbol.getBaseVolume(), symbol.getVolumeVariance());
        double quoteVolume = baseVolume * ((openPrice + closePrice) / 2.0);
        long tradeCount = randomTradeCount(random, symbol);

        return KlineEvent.builder()
                .eventTime(closeTime)
                .exchange(request.getExchange())
                .ingestTime(now)
                .interval(request.getIntervalLabel())
                .symbol(symbol.getSymbol())
                .kline(KlineEvent.Window.builder()
                        .baseVolume(formatDecimal(baseVolume))
                        .quoteVolume(formatDecimal(quoteVolume))
                        .openPrice(formatDecimal(openPrice))
                        .closePrice(formatDecimal(closePrice))
                        .highPrice(formatDecimal(highPrice))
                        .lowPrice(formatDecimal(lowPrice))
                        .startTime(startTime)
                        .closeTime(closeTime)
                        .closed(request.isClosed())
                        .tradeCount(tradeCount)
                        .build())
                .build();
    }

    private void send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish Kafka message", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize kafka payload", e);
        }
    }

    private double priceWithVariance(ThreadLocalRandom random, double base, double variance) {
        double deltaRatio = (random.nextDouble() * 2 - 1) * Math.max(0.0001, variance);
        double value = base * (1 + deltaRatio);
        return Math.max(0.00000001, value);
    }

    private double magnitudeWithVariance(ThreadLocalRandom random, double base, double variance) {
        double deltaRatio = (random.nextDouble() * 2 - 1) * Math.max(0.0, variance);
        double value = base * (1 + deltaRatio);
        return Math.max(0.00000001, value);
    }

    private long randomTradeCount(ThreadLocalRandom random, KafkaKlineRequest.SymbolConfig symbol) {
        int min = Math.max(1, symbol.getMinTradeCount());
        int max = Math.max(min, symbol.getMaxTradeCount());
        if (max == min) {
            return min;
        }
        return random.nextInt((max - min) + 1) + min;
    }

    private String formatDecimal(double value) {
        return BigDecimal.valueOf(value)
                .setScale(8, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
