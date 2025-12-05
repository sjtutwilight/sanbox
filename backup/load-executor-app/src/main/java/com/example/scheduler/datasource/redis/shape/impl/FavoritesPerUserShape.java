package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Per-user favorite set/zset writer.
 */
public class FavoritesPerUserShape implements RedisDataShape {

    @Override
    public GenerationPattern pattern() {
        return GenerationPattern.FAV_NORMAL_SET_ZSET;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        RequestDefaults defaults = context.getDefaults();
        context.getRedis().pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long userId = context.getStartIndex() + i + 1;
                String setKey = defaults.getKeyPrefix() + "fav:set:" + userId;
                String zsetKey = defaults.getKeyPrefix() + "fav:z:" + userId;
                ArrayList<String> symbols = RedisPayloadBuilder.randomSymbols(defaults.getFavPerUser());
                long now = Instant.now().toEpochMilli();
                for (int j = 0; j < symbols.size(); j++) {
                    String sym = symbols.get(j);
                    ops.addToSet(setKey, sym);
                    ops.addToZset(zsetKey, sym, now - j);
                }
                ops.expire(setKey, defaults.getTtlSeconds());
                ops.expire(zsetKey, defaults.getTtlSeconds());
            }
        });
    }
}
