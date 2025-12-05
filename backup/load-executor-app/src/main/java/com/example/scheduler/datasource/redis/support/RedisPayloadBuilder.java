package com.example.scheduler.datasource.redis.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared payload helpers for Redis data generation.
 */
public final class RedisPayloadBuilder {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String> POPULAR_SYMBOLS = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "DOGEUSDT", "OPUSDT",
            "ARBUSDT", "XRPUSDT", "LTCUSDT", "LINKUSDT", "AVAXUSDT",
            "APTUSDT", "SUIUSDT"
    );

    private RedisPayloadBuilder() {
    }

    public static Map<String, Object> positionPayload(long userId, int valueSizeBytes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("symbol", "BTCUSDT");
        payload.put("size", ThreadLocalRandom.current().nextDouble(0, 50));
        payload.put("lastUpdate", Instant.now().toEpochMilli());
        payload.put("padding", padding(valueSizeBytes));
        return payload;
    }

    public static String tradePayload(long tradeId, int valueSizeBytes) {
        String base = "trade_id:" + tradeId + "|ts:" + Instant.now().toEpochMilli() + "|";
        String pad = padding(Math.max(0, valueSizeBytes - base.length()));
        return base + pad;
    }

    public static String padding(int targetSize) {
        if (targetSize <= 0) {
            return "";
        }
        char[] chars = new char[targetSize];
        Arrays.fill(chars, 'x');
        return new String(chars);
    }

    public static ArrayList<String> randomSymbols(int count) {
        ArrayList<String> symbols = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            symbols.add("SYM" + ThreadLocalRandom.current().nextInt(1, 5000));
        }
        return symbols;
    }

    public static String buildSymbolKey(long ordinal) {
        return String.format("sym:%05d", ordinal + 1);
    }

    public static String buildDisplaySymbol(long ordinal) {
        int index = (int) (ordinal % POPULAR_SYMBOLS.size());
        return POPULAR_SYMBOLS.get(index);
    }

    public static String resolveDay(String day) {
        if (day == null || day.isBlank()) {
            return LocalDate.now().format(DAY_FORMATTER);
        }
        return day;
    }

    public static String buildKlinePayload(String symbol, String interval, long ts, int valueSizeBytes) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double base = random.nextDouble(10, 50000);
        double open = base;
        double close = base + random.nextDouble(-50, 50);
        double high = Math.max(open, close) + random.nextDouble(0, 30);
        double low = Math.min(open, close) - random.nextDouble(0, 30);
        double volume = random.nextDouble(10, 10000);
        String baseStr = String.format(Locale.US,
                "sym=%s|tf=%s|ts=%d|o=%.2f|h=%.2f|l=%.2f|c=%.2f|v=%.2f|",
                symbol, interval, ts, open, high, low, close, volume);
        String pad = padding(Math.max(0, valueSizeBytes - baseStr.length()));
        return baseStr + pad;
    }

    public static long intervalToMillis(String interval) {
        if (interval == null || interval.isBlank()) {
            return 60_000L;
        }
        String value = interval.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("ms")) {
                return Long.parseLong(value.replace("ms", ""));
            } else if (value.endsWith("s")) {
                return Long.parseLong(value.replace("s", "")) * 1000L;
            } else if (value.endsWith("m")) {
                return Long.parseLong(value.replace("m", "")) * 60_000L;
            } else if (value.endsWith("h")) {
                return Long.parseLong(value.replace("h", "")) * 3_600_000L;
            } else if (value.endsWith("d")) {
                return Long.parseLong(value.replace("d", "")) * 86_400_000L;
            }
        } catch (NumberFormatException ignore) {
            // ignore invalid value
        }
        return 60_000L;
    }

    public static int windowForInterval(String interval, int baseWindow) {
        long minuteRatio = Math.max(1, intervalToMillis(interval) / 60_000L);
        long window = Math.max(1, baseWindow / minuteRatio);
        return (int) Math.max(1, window);
    }
}
