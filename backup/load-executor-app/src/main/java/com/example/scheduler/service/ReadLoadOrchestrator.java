package com.example.scheduler.service;

import com.example.scheduler.experiment.Experiment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Encapsulates read load behavior for experiments.
 */
@Service
@RequiredArgsConstructor
public class ReadLoadOrchestrator {

    private final StringRedisTemplate redisTemplate;
    private final FavoriteCacheService favoriteCacheService;

    public String performRead(Experiment.ReadLoadConfig config,
                              Experiment.CacheStrategy cacheStrategy,
                              int userId) {
        String keyPrefix = config.getKeyPrefix() != null ? config.getKeyPrefix() : "dg:";
        return switch (config.getReadMode()) {
            case ZSET_RANGE -> {
                if (cacheStrategy == Experiment.CacheStrategy.CACHE_ASIDE) {
                    favoriteCacheService.getFavorites(userId, keyPrefix, config.getTopN());
                } else {
                    String zsetKey = keyPrefix + "fav:z:" + userId;
                    redisTemplate.opsForZSet().reverseRange(zsetKey, 0, config.getTopN() - 1);
                }
                yield "key=" + keyPrefix + "fav:z:" + userId + " topN=" + config.getTopN();
            }
            case SET_ISMEMBER -> {
                String setKey = keyPrefix + "fav:set:" + userId;
                redisTemplate.opsForSet().isMember(setKey, String.valueOf(userId % 1000));
                yield "key=" + setKey + " member=" + (userId % 1000);
            }
            case HASH_GETALL -> {
                String hashKey = keyPrefix + "pos:user:" + userId;
                redisTemplate.opsForHash().entries(hashKey);
                yield "key=" + hashKey;
            }
            case LIST_RANGE -> {
                String listKey = keyPrefix + "trades:BTCUSDT";
                int listTopN = config.getTopN();
                int listEnd = listTopN > 0 ? listTopN - 1 : -1;
                redisTemplate.opsForList().range(listKey, 0, listEnd);
                yield "key=" + listKey + " topN=" + (listTopN > 0 ? listTopN : -1);
            }
            case BITMAP_GETBIT -> {
                String bitmapKey = keyPrefix + "active_users_bitmap:" + java.time.LocalDate.now();
                redisTemplate.opsForValue().getBit(bitmapKey, userId);
                yield "key=" + bitmapKey + " offset=" + userId;
            }
            case KLINE_LIST_RECENT -> {
                String symbolKey = buildKlineSymbolKey(userId);
                String klineListKey = keyPrefix + symbolKey;
                int latest = config.getTopN();
                int end = latest > 0 ? latest - 1 : -1;
                redisTemplate.opsForList().range(klineListKey, 0, end);
                yield "key=" + klineListKey + " topN=" + (latest > 0 ? latest : -1);
            }
            case KLINE_ZSET_RANGE -> {
                String klineZsetKey = keyPrefix + buildKlineSymbolKey(userId);
                int rangeSize = config.getTopN() > 0 ? config.getTopN() : 1000;
                long now = System.currentTimeMillis();
                long intervalMillis = resolveKlineIntervalMillis(keyPrefix, ":kline:zset:");
                long windowMillis = intervalMillis * (long) Math.max(1, rangeSize);
                redisTemplate.opsForZSet().reverseRangeByScore(klineZsetKey, now - windowMillis, now, 0, rangeSize);
                yield "key=" + klineZsetKey + " window=" + rangeSize;
            }
        };
    }

    private String buildKlineSymbolKey(int userId) {
        int ordinal = Math.max(1, userId);
        return String.format("sym:%05d", ordinal);
    }

    private long resolveKlineIntervalMillis(String keyPrefix, String marker) {
        if (keyPrefix == null || marker == null) {
            return 60_000L;
        }
        int idx = keyPrefix.indexOf(marker);
        if (idx < 0) {
            return 60_000L;
        }
        int start = idx + marker.length();
        int end = keyPrefix.indexOf(':', start);
        if (end < 0) {
            end = keyPrefix.length();
        }
        String interval = keyPrefix.substring(start, end);
        return parseIntervalMillis(interval);
    }

    private long parseIntervalMillis(String interval) {
        if (interval == null || interval.isBlank()) {
            return 60_000L;
        }
        String value = interval.trim().toLowerCase();
        try {
            if (value.endsWith("ms")) {
                return Long.parseLong(value.replace("ms", ""));
            }
            if (value.endsWith("s")) {
                return Long.parseLong(value.replace("s", "")) * 1000L;
            }
            if (value.endsWith("m")) {
                return Long.parseLong(value.replace("m", "")) * 60_000L;
            }
            if (value.endsWith("h")) {
                return Long.parseLong(value.replace("h", "")) * 3_600_000L;
            }
            if (value.endsWith("d")) {
                return Long.parseLong(value.replace("d", "")) * 86_400_000L;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return 60_000L;
    }
}
