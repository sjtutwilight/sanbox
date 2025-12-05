package com.example.scheduler.datagenerator.support;

import com.example.scheduler.config.DataGeneratorProperties;
import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies default values to incoming {@link DataGenerationRequest}s and exposes resolved view objects.
 */
@Component
@RequiredArgsConstructor
public class RequestDefaultsResolver {

    private static final int DEFAULT_KLINE_SYMBOLS = 200;
    private static final int DEFAULT_KLINE_CANDLES = 1440;
    private static final List<String> DEFAULT_KLINE_INTERVALS = List.of("1m", "5m", "1h", "1d");

    private final DataGeneratorProperties properties;

    public void applyDefaults(DataGenerationRequest request) {
        RequestDefaults defaults = resolve(request);
        if (request.getValueSizeBytes() == null) {
            request.setValueSizeBytes(defaults.getValueSizeBytes());
        }
        if (request.getBatchSize() == null) {
            request.setBatchSize(defaults.getBatchSize());
        }
        if (request.getTtlSeconds() == null) {
            request.setTtlSeconds(defaults.getTtlSeconds());
        }
        if (request.getKeyPrefix() == null) {
            request.setKeyPrefix(defaults.getKeyPrefix());
        }
        if (request.getListWindow() == null) {
            request.setListWindow(defaults.getListWindow());
        }
        if (request.getFavPerUser() == null) {
            request.setFavPerUser(defaults.getFavPerUser());
        }
        if (request.getSymbolCount() == null) {
            request.setSymbolCount(defaults.getSymbolCount());
        }
        if (request.getCandlesPerSymbol() == null) {
            request.setCandlesPerSymbol(defaults.getCandlesPerSymbol());
        }
        if (request.getIncludeZset() == null) {
            request.setIncludeZset(defaults.isIncludeZset());
        }
        if (request.getKlineIntervals() == null || request.getKlineIntervals().isEmpty()) {
            request.setKlineIntervals(defaults.getKlineIntervals());
        }
    }

    public RequestDefaults resolve(DataGenerationRequest request) {
        return RequestDefaults.builder()
                .valueSizeBytes(resolveInt(request.getValueSizeBytes(), properties.getDefaultValueSizeBytes()))
                .batchSize(resolveInt(request.getBatchSize(), properties.getDefaultBatchSize()))
                .ttlSeconds(resolveInteger(request.getTtlSeconds(), properties.getDefaultTtlSeconds()))
                .keyPrefix(request.getKeyPrefix() != null ? request.getKeyPrefix() : properties.getDefaultKeyPrefix())
                .listWindow(resolveInt(request.getListWindow(), properties.getDefaultListWindow()))
                .favPerUser(resolveInt(request.getFavPerUser(), properties.getDefaultFavPerUser()))
                .symbolCount(resolveInt(request.getSymbolCount(), DEFAULT_KLINE_SYMBOLS))
                .candlesPerSymbol(resolveInt(request.getCandlesPerSymbol(), DEFAULT_KLINE_CANDLES))
                .includeZset(request.getIncludeZset() == null || Boolean.TRUE.equals(request.getIncludeZset()))
                .klineIntervals(resolveIntervals(request.getKlineIntervals()))
                .build();
    }

    private int resolveInt(Integer provided, int defaultValue) {
        return provided != null && provided > 0 ? provided : defaultValue;
    }

    private Integer resolveInteger(Integer provided, int defaultValue) {
        return provided != null && provided > 0 ? provided : defaultValue;
    }

    private List<String> resolveIntervals(List<String> provided) {
        if (provided == null || provided.isEmpty()) {
            return DEFAULT_KLINE_INTERVALS;
        }
        return provided;
    }
}
