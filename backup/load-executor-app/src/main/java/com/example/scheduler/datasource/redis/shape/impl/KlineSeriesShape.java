package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.client.RedisStructureClient;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeAppendContext;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Multi-window kline writer for both bounded and giant strategies.
 */
@RequiredArgsConstructor
public class KlineSeriesShape implements RedisDataShape {

    private final GenerationPattern pattern;
    private final boolean shouldTrim;

    @Override
    public GenerationPattern pattern() {
        return pattern;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        RequestDefaults defaults = context.getDefaults();
        List<String> intervals = defaults.getKlineIntervals();
        int candlesPerSymbol = defaults.getCandlesPerSymbol();
        int baseWindow = defaults.getListWindow();
        boolean includeZset = defaults.isIncludeZset();
        context.getRedis().pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long symbolOrdinal = context.getStartIndex() + i;
                String symbolKey = RedisPayloadBuilder.buildSymbolKey(symbolOrdinal);
                String displaySymbol = RedisPayloadBuilder.buildDisplaySymbol(symbolOrdinal);
                for (String interval : intervals) {
                    String listKey = defaults.getKeyPrefix() + "kline:list:" + interval + ":" + symbolKey;
                    String zsetKey = defaults.getKeyPrefix() + "kline:zset:" + interval + ":" + symbolKey;
                    long intervalMillis = RedisPayloadBuilder.intervalToMillis(interval);
                    writeSymbolKlines(ops, listKey, zsetKey, interval, intervalMillis,
                            candlesPerSymbol, defaults.getValueSizeBytes(), includeZset, displaySymbol);
                    if (shouldTrim) {
                        int window = Math.max(1, Math.min(candlesPerSymbol,
                                RedisPayloadBuilder.windowForInterval(interval, baseWindow)));
                        ops.trimList(listKey, 0, window - 1);
                    }
                    ops.expire(listKey, defaults.getTtlSeconds());
                    if (includeZset) {
                        ops.expire(zsetKey, defaults.getTtlSeconds());
                    }
                }
            }
        });
    }

    @Override
    public boolean supportsAppend() {
        return true;
    }

    @Override
    public void append(RedisDataShapeAppendContext context) {
        RequestDefaults defaults = context.getDefaults();
        List<String> intervals = defaults.getKlineIntervals();
        boolean includeZset = defaults.isIncludeZset();
        int baseWindow = defaults.getListWindow();
        context.getRedis().pipeline(ops -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < context.getBatchSize(); i++) {
                long symbolOrdinal = random.nextLong(0, Math.max(1, defaults.getSymbolCount()));
                String symbolKey = RedisPayloadBuilder.buildSymbolKey(symbolOrdinal);
                String displaySymbol = RedisPayloadBuilder.buildDisplaySymbol(symbolOrdinal);
                String interval = intervals.get(random.nextInt(intervals.size()));
                String listKey = defaults.getKeyPrefix() + "kline:list:" + interval + ":" + symbolKey;
                String zsetKey = defaults.getKeyPrefix() + "kline:zset:" + interval + ":" + symbolKey;
                long ts = Instant.now().toEpochMilli();
                String payload = RedisPayloadBuilder.buildKlinePayload(displaySymbol, interval, ts, defaults.getValueSizeBytes());
                ops.leftPush(listKey, payload);
                if (includeZset) {
                    ops.addToZset(zsetKey, payload, ts);
                }
                if (shouldTrim) {
                    int window = Math.max(1, Math.min(defaults.getCandlesPerSymbol(),
                            RedisPayloadBuilder.windowForInterval(interval, baseWindow)));
                    ops.trimList(listKey, 0, window - 1);
                }
                ops.expire(listKey, defaults.getTtlSeconds());
                if (includeZset) {
                    ops.expire(zsetKey, defaults.getTtlSeconds());
                }
            }
        });
    }

    private void writeSymbolKlines(RedisStructureClient.StructureOperations ops,
                                   String listKey,
                                   String zsetKey,
                                   String interval,
                                   long intervalMillis,
                                   int candlesPerSymbol,
                                   int valueSizeBytes,
                                   boolean includeZset,
                                   String displaySymbol) {
        long baseTs = Instant.now().toEpochMilli();
        for (int c = 0; c < candlesPerSymbol; c++) {
            long ts = baseTs - c * intervalMillis;
            String payload = RedisPayloadBuilder.buildKlinePayload(displaySymbol, interval, ts, valueSizeBytes);
            ops.leftPush(listKey, payload);
            if (includeZset) {
                ops.addToZset(zsetKey, payload, ts);
            }
        }
    }
}
