package com.example.scheduler.loadexecutor.experiment.support;

import java.time.Duration;
import java.util.Map;

/**
 * Utility helpers for parsing loosely typed payload values into strongly typed numbers/booleans/durations.
 */
public final class RequestSupport {

    private RequestSupport() {
    }

    public static long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static boolean boolValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s.trim());
        }
        return defaultValue;
    }

    public static Duration durationValue(Object value, Duration defaultValue) {
        if (value instanceof Duration duration) {
            return duration;
        }
        if (value instanceof Number number) {
            return Duration.ofSeconds(Math.max(1, number.longValue()));
        }
        if (value instanceof String s && !s.isBlank()) {
            String trimmed = s.trim().toLowerCase();
            try {
                if (trimmed.endsWith("ms")) {
                    long millis = Long.parseLong(trimmed.substring(0, trimmed.length() - 2));
                    return Duration.ofMillis(Math.max(1, millis));
                }
                if (trimmed.endsWith("s")) {
                    long seconds = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                    return Duration.ofSeconds(Math.max(1, seconds));
                }
                if (trimmed.endsWith("m")) {
                    long minutes = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                    return Duration.ofMinutes(Math.max(1, minutes));
                }
                long seconds = Long.parseLong(trimmed);
                return Duration.ofSeconds(Math.max(1, seconds));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
