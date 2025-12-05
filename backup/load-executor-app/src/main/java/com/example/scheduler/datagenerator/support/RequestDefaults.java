package com.example.scheduler.datagenerator.support;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Resolved default values for {@link com.example.scheduler.datagenerator.model.DataGenerationRequest}.
 */
@Getter
@Builder
public class RequestDefaults {

    private final int valueSizeBytes;
    private final int batchSize;
    private final Integer ttlSeconds;
    private final String keyPrefix;
    private final int listWindow;
    private final int favPerUser;
    private final int symbolCount;
    private final int candlesPerSymbol;
    private final boolean includeZset;
    private final List<String> klineIntervals;
}
