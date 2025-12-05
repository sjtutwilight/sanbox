package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.client.RedisStructureClient;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;

/**
 * Hash structure for per-user positions.
 */
public class PerUserPositionShape implements RedisDataShape {

    @Override
    public GenerationPattern pattern() {
        return GenerationPattern.USER_POSITION_PER_USER_HASH;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        RequestDefaults defaults = context.getDefaults();
        boolean overwrite = context.getRequest().getOverwrite() == null
                || Boolean.TRUE.equals(context.getRequest().getOverwrite());
        RedisStructureClient redis = context.getRedis();
        redis.pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long userId = context.getStartIndex() + i + 1;
                String key = defaults.getKeyPrefix() + "pos:user:" + userId;
                if (!overwrite && redis.hasKey(key)) {
                    continue;
                }
                ops.putAllHash(key, RedisPayloadBuilder.positionPayload(userId, defaults.getValueSizeBytes()));
                ops.expire(key, defaults.getTtlSeconds());
            }
        });
    }
}
