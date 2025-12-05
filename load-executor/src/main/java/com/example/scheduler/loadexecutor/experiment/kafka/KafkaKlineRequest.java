package com.example.scheduler.loadexecutor.experiment.kafka;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.HotKeyConfig;
import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import lombok.Value;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Value
public class KafkaKlineRequest {

    String topic;
    String exchange;
    String intervalLabel;
    Duration interval;
    boolean closed;
    List<SymbolConfig> symbols;
    HotKeyConfig hotKeyConfig;

    public static KafkaKlineRequest from(OperationInvocationContext context) {
        Command command = context.getCommand();
        Map<String, Object> merged = new HashMap<>();
        if (command.getDataRequest() != null) {
            merged.putAll(command.getDataRequest());
        }
        if (command.getOverrides() != null) {
            merged.putAll(command.getOverrides());
        }
        if (context.getPayload() != null) {
            merged.putAll(context.getPayload());
        }
        String topic = string(merged.get("topic"), "binance.kline");
        String exchange = string(merged.get("exchange"), "binance");
        String intervalLabel = intervalLabel(merged);
        Duration interval = parseInterval(merged, Duration.ofMinutes(1));
        boolean closed = bool(merged.get("closed"), true);
        List<SymbolConfig> symbols = parseSymbols(merged.get("symbols"));
        HotKeyConfig hotKeyConfig = context.getPhase() != null ? context.getPhase().getHotKeyConfig() : null;
        return new KafkaKlineRequest(topic, exchange, intervalLabel, interval, closed, symbols, hotKeyConfig);
    }

    public SymbolConfig pickSymbol() {
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("No symbol configuration available");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (hotKeyConfig != null && hotKeyConfig.getHotKeyCount() > 0) {
            return pickUsingHotKey(random);
        }
        int totalWeight = symbols.stream().mapToInt(SymbolConfig::getWeight).sum();
        int cursor = random.nextInt(Math.max(1, totalWeight));
        for (SymbolConfig config : symbols) {
            cursor -= config.getWeight();
            if (cursor < 0) {
                return config;
            }
        }
        return symbols.get(symbols.size() - 1);
    }

    private SymbolConfig pickUsingHotKey(ThreadLocalRandom random) {
        int hotCount = Math.min(hotKeyConfig.getHotKeyCount(), symbols.size());
        if (hotCount <= 0) {
            return symbols.get(symbols.size() - 1);
        }
        boolean hitHot = random.nextDouble() < hotKeyConfig.getClampedRatio();
        if (hitHot) {
            return symbols.get(random.nextInt(hotCount));
        }
        return symbols.get(random.nextInt(symbols.size()));
    }

    private static String string(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? defaultValue : s;
    }

    private static boolean bool(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private static Duration parseInterval(Map<String, Object> payload, Duration defaultValue) {
        Object raw = payload.get("interval");
        if (raw instanceof Number number) {
            return Duration.ofSeconds(Math.max(1, number.longValue()));
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Duration.ofMillis(parseDurationString(s.trim(), defaultValue.toMillis()));
        }
        Object seconds = payload.get("intervalSeconds");
        if (seconds instanceof Number number) {
            return Duration.ofSeconds(Math.max(1, number.longValue()));
        }
        return defaultValue;
    }

    private static String intervalLabel(Map<String, Object> payload) {
        Object raw = payload.get("interval");
        if (raw instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        Object seconds = payload.get("intervalSeconds");
        if (seconds instanceof Number number) {
            long value = number.longValue();
            return value + "s";
        }
        return "1m";
    }

    private static long parseDurationString(String value, long defaultMillis) {
        String lower = value.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith("ms")) {
                return Long.parseLong(lower.substring(0, lower.length() - 2));
            }
            if (lower.endsWith("s")) {
                long seconds = Long.parseLong(lower.substring(0, lower.length() - 1));
                return seconds * 1000;
            }
            if (lower.endsWith("m")) {
                long minutes = Long.parseLong(lower.substring(0, lower.length() - 1));
                return minutes * 60_000;
            }
            if (lower.endsWith("h")) {
                long hours = Long.parseLong(lower.substring(0, lower.length() - 1));
                return hours * 3_600_000;
            }
            if (lower.endsWith("d")) {
                long days = Long.parseLong(lower.substring(0, lower.length() - 1));
                return days * 86_400_000;
            }
            return Long.parseLong(lower);
        } catch (NumberFormatException e) {
            return defaultMillis;
        }
    }

    private static List<SymbolConfig> parseSymbols(Object raw) {
        List<SymbolConfig> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                SymbolConfig config = parseSymbolItem(item);
                if (config != null) {
                    result.add(config);
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                SymbolConfig config = parseSymbolEntry(entry.getKey(), entry.getValue());
                if (config != null) {
                    result.add(config);
                }
            }
        } else if (raw instanceof String s && !s.isBlank()) {
            String[] parts = s.split(",");
            for (String part : parts) {
                SymbolConfig config = SymbolConfig.fromDelimited(part.trim());
                if (config != null) {
                    result.add(config);
                }
            }
        }
        if (result.isEmpty()) {
            result.addAll(SymbolConfig.defaults());
        }
        return List.copyOf(result);
    }

    private static SymbolConfig parseSymbolItem(Object item) {
        if (item instanceof Map<?, ?> map) {
            return SymbolConfig.fromMap((Map<?, ?>) map, null);
        }
        if (item instanceof String s && !s.isBlank()) {
            return SymbolConfig.fromDelimited(s.trim());
        }
        return null;
    }

    private static SymbolConfig parseSymbolEntry(Object key, Object value) {
        if (value instanceof Map<?, ?> map) {
            return SymbolConfig.fromMap((Map<?, ?>) map, key != null ? key.toString() : null);
        }
        Map<String, Object> simple = new HashMap<>();
        simple.put("symbol", key != null ? key.toString() : null);
        simple.put("weight", value);
        return SymbolConfig.fromMap(simple, null);
    }

    @Value
    public static class SymbolConfig {
        String symbol;
        int weight;
        double basePrice;
        double priceVariance;
        double baseVolume;
        double volumeVariance;
        int minTradeCount;
        int maxTradeCount;

        static List<SymbolConfig> defaults() {
            return List.of(
                    new SymbolConfig("BTCUSDT", 80, 93000, 0.004, 5.0, 0.5, 800, 2400),
                    new SymbolConfig("ETHUSDT", 15, 3500, 0.008, 30.0, 0.6, 400, 1600),
                    new SymbolConfig("SOLUSDT", 5, 150, 0.015, 500, 0.7, 200, 800)
            );
        }

        static SymbolConfig fromMap(Map<?, ?> map, String fallbackSymbol) {
            if (map == null || map.isEmpty()) {
                return null;
            }
            String symbol = fallbackSymbol != null ? fallbackSymbol : string(map.get("symbol"), null);
            if (symbol == null) {
                return null;
            }
            int weight = Math.max(1, intValue(map.get("weight"), 1));
            double basePrice = doubleValue(map.get("basePrice"), guessBasePrice(symbol));
            double priceVariance = doubleValue(map.get("priceVariance"), 0.006);
            double baseVolume = doubleValue(map.get("baseVolume"), defaultBaseVolume(basePrice));
            double volumeVariance = doubleValue(map.get("volumeVariance"), 0.6);
            int minTrade = Math.max(1, intValue(map.get("minTradeCount"), 200));
            int maxTrade = Math.max(minTrade, intValue(map.get("maxTradeCount"), minTrade * 3));
            return new SymbolConfig(symbol, weight, basePrice, priceVariance, baseVolume, volumeVariance, minTrade, maxTrade);
        }

        static SymbolConfig fromDelimited(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String[] tokens = value.split(":");
            String symbol = tokens[0].trim();
            int weight = tokens.length > 1 ? Math.max(1, parseInt(tokens[1], 1)) : 1;
            double basePrice = tokens.length > 2 ? parseDouble(tokens[2], guessBasePrice(symbol)) : guessBasePrice(symbol);
            return new SymbolConfig(symbol, weight, basePrice, 0.006, defaultBaseVolume(basePrice), 0.6, 200, 800);
        }

        private static int intValue(Object value, int defaultValue) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                return parseInt(s, defaultValue);
            }
            return defaultValue;
        }

        private static double doubleValue(Object value, double defaultValue) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                return parseDouble(s, defaultValue);
            }
            return defaultValue;
        }

        private static int parseInt(String value, int defaultValue) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private static double parseDouble(String value, double defaultValue) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private static double guessBasePrice(String symbol) {
            return PriceHints.lookup(symbol);
        }

        private static double defaultBaseVolume(double basePrice) {
            if (basePrice >= 10000) {
                return 2.0;
            }
            if (basePrice >= 1000) {
                return 5.0;
            }
            if (basePrice >= 100) {
                return 40.0;
            }
            if (basePrice >= 10) {
                return 400.0;
            }
            return 3000.0;
        }

        private static String string(Object value, String defaultValue) {
            if (value == null) {
                return defaultValue;
            }
            String s = value.toString().trim();
            return s.isEmpty() ? defaultValue : s;
        }
    }

    private static class PriceHints {
        private static final Map<String, Double> HINTS = Map.ofEntries(
                Map.entry("BTCUSDT", 93000d),
                Map.entry("ETHUSDT", 3500d),
                Map.entry("SOLUSDT", 150d),
                Map.entry("BNBUSDT", 600d),
                Map.entry("DOGEUSDT", 0.2d),
                Map.entry("XRPUSDT", 0.55d),
                Map.entry("ADAUSDT", 0.45d),
                Map.entry("OPUSDT", 3.0d),
                Map.entry("ARBUSDT", 1.2d)
        );

        static double lookup(String symbol) {
            return HINTS.getOrDefault(symbol.toUpperCase(Locale.ROOT), 100.0);
        }
    }
}
