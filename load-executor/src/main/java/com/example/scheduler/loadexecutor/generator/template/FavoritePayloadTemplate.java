package com.example.scheduler.loadexecutor.generator.template;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.HotKeyConfig;
import com.example.scheduler.loadexecutor.domain.LoadPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class FavoritePayloadTemplate implements ExperimentPayloadTemplate {

    private static final String EXPERIMENT_ID = "favorite";

    @Override
    public boolean supports(Command command) {
        return EXPERIMENT_ID.equalsIgnoreCase(command.getExperimentId());
    }

    @Override
    public Map<String, Object> produce(Command command, LoadPhase phase, long sequence) {
        Map<String, Object> source = command.getDataRequest();
        if ((source == null || source.isEmpty()) && command.getOverrides() != null && !command.getOverrides().isEmpty()) {
            source = command.getOverrides();
        }
        FavoriteConfig config = FavoriteConfig.from(source);
        long userId = config.pickUserId(phase.getHotKeyConfig());
        String symbol = config.pickSymbol();
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("symbol", symbol);
        payload.put("tags", config.tags);
        if (config.ttlSeconds != null) {
            payload.put("ttlSeconds", config.ttlSeconds);
        }
        return payload;
    }

    private record FavoriteConfig(long startUserId, int userCount, List<String> symbols, String tags, Long ttlSeconds) {

        static FavoriteConfig from(Map<String, Object> payload) {
            long startUserId = getLong(payload, "startUserId", 1L);
            int userCount = (int) getLong(payload, "userCount", 100L);
            List<String> symbols = getList(payload, "symbols", List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"));
            String tags = payload != null && payload.get("tags") != null ? payload.get("tags").toString() : "generated";
            Long ttl = getNullableLong(payload, "ttlSeconds");
            return new FavoriteConfig(startUserId, userCount, symbols, tags, ttl);
        }

        long pickUserId(HotKeyConfig hotKeyConfig) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int range = Math.max(1, userCount);
            int index;
            if (hotKeyConfig != null && hotKeyConfig.getHotKeyCount() > 0) {
                boolean hitHot = random.nextDouble() < hotKeyConfig.getClampedRatio();
                int hotSize = Math.min(range, hotKeyConfig.getHotKeyCount());
                if (hitHot) {
                    index = random.nextInt(hotSize);
                } else {
                    index = random.nextInt(range - hotSize) + hotSize;
                }
            } else {
                index = random.nextInt(range);
            }
            return startUserId + index;
        }

        String pickSymbol() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            return symbols.get(random.nextInt(symbols.size()));
        }

        private static long getLong(Map<String, Object> payload, String key, long defaultValue) {
            if (payload == null) {
                return defaultValue;
            }
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                return Long.parseLong(s);
            }
            return defaultValue;
        }

        private static Long getNullableLong(Map<String, Object> payload, String key) {
            if (payload == null) {
                return null;
            }
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                return Long.parseLong(s);
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private static List<String> getList(Map<String, Object> payload, String key, List<String> defaults) {
            if (payload == null) {
                return defaults;
            }
            Object value = payload.get(key);
            if (value instanceof List<?> list && !list.isEmpty()) {
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    result.add(String.valueOf(item));
                }
                return result;
            }
            if (value instanceof String s && !s.isBlank()) {
                String[] parts = s.split(",");
                List<String> result = new ArrayList<>();
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
            return defaults;
        }
    }
}
