package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;
import lombok.RequiredArgsConstructor;

/**
 * Trade list writer with optional trimming.
 */
@RequiredArgsConstructor
public class TradeListShape implements RedisDataShape {

    private final GenerationPattern pattern;
    private final boolean shouldTrim;

    @Override
    public GenerationPattern pattern() {
        return pattern;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        RequestDefaults defaults = context.getDefaults();
        String key = defaults.getKeyPrefix() + "trades:BTCUSDT";
        context.getRedis().pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long tradeId = context.getStartIndex() + i + 1;
                ops.leftPush(key, RedisPayloadBuilder.tradePayload(tradeId, defaults.getValueSizeBytes()));
            }
            if (shouldTrim) {
                ops.trimList(key, 0, defaults.getListWindow() - 1);
            }
            ops.expire(key, defaults.getTtlSeconds());
        });
    }
}
