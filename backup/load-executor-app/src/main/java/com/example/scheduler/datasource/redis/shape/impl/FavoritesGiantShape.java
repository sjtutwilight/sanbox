package com.example.scheduler.datasource.redis.shape.impl;

import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.datagenerator.support.RequestDefaults;
import com.example.scheduler.datasource.redis.shape.RedisDataShape;
import com.example.scheduler.datasource.redis.shape.RedisDataShapeContext;
import com.example.scheduler.datasource.redis.support.RedisPayloadBuilder;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Giant set/zset storing all favorites.
 */
public class FavoritesGiantShape implements RedisDataShape {

    @Override
    public GenerationPattern pattern() {
        return GenerationPattern.FAV_GIANT_SET_ZSET;
    }

    @Override
    public void write(RedisDataShapeContext context) {
        RequestDefaults defaults = context.getDefaults();
        String setKey = defaults.getKeyPrefix() + "fav:all:set";
        String zsetKey = defaults.getKeyPrefix() + "fav:all:z";
        context.getRedis().pipeline(ops -> {
            for (int i = 0; i < context.getCount(); i++) {
                long userId = context.getStartIndex() + i + 1;
                ArrayList<String> symbols = RedisPayloadBuilder.randomSymbols(defaults.getFavPerUser());
                long now = Instant.now().toEpochMilli();
                for (int j = 0; j < symbols.size(); j++) {
                    String sym = "u" + userId + ":" + symbols.get(j);
                    ops.addToSet(setKey, sym);
                    ops.addToZset(zsetKey, sym, now - j);
                }
            }
            ops.expire(setKey, defaults.getTtlSeconds());
            ops.expire(zsetKey, defaults.getTtlSeconds());
        });
    }
}
