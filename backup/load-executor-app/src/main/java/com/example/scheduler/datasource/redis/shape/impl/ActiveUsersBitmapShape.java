package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;

/**
 * Daily active users stored in a bitmap.
 */
public class ActiveUsersBitmapShape implements RedisDataShape {

    @Override
    public GenerationPattern pattern() {
        return GenerationPattern.ACTIVE_USERS_BITMAP;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        String key = context.getDefaults().getKeyPrefix() + "active_users_bitmap:" +
                RedisPayloadBuilder.resolveDay(context.getRequest().getDay());
        context.getRedis().pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long userId = context.getStartIndex() + i + 1;
                ops.setBit(key, userId, true);
            }
            ops.expire(key, context.getDefaults().getTtlSeconds());
        });
    }
}
