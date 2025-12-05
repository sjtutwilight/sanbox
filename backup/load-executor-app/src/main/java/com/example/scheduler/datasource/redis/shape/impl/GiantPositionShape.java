package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;

/**
 * Giant hash keyed by user field.
 */
public class GiantPositionShape implements RedisDataShape {

    @Override
    public GenerationPattern pattern() {
        return GenerationPattern.USER_POSITION_GIANT_HASH;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        RequestDefaults defaults = context.getDefaults();
        String key = defaults.getKeyPrefix() + "pos:all";
        context.getRedis().pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long userId = context.getStartIndex() + i + 1;
                String field = "user:" + userId;
                ops.putHashField(key, field, RedisPayloadBuilder.positionPayload(userId, defaults.getValueSizeBytes()));
            }
            ops.expire(key, defaults.getTtlSeconds());
        });
    }
}
