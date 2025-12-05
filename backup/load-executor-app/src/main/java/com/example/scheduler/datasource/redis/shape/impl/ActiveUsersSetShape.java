package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;

/**
 * Daily active users stored in a Set.
 */
public class ActiveUsersSetShape implements RedisDataShape {

    @Override
    public GenerationPattern pattern() {
        return GenerationPattern.ACTIVE_USERS_DAILY_SET;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        String key = context.getDefaults().getKeyPrefix() + "active_users:" +
                RedisPayloadBuilder.resolveDay(context.getRequest().getDay());
        context.getRedis().pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long userId = context.getStartIndex() + i + 1;
                ops.addToSet(key, "user:" + userId);
            }
            ops.expire(key, context.getDefaults().getTtlSeconds());
        });
    }
}
