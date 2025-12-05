package com.example.scheduler.loadexecutor.planner;

import com.example.scheduler.loadexecutor.config.LoadExecutorProperties;
import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.HotKeyConfig;
import com.example.scheduler.loadexecutor.domain.LoadPhase;
import com.example.scheduler.loadexecutor.domain.LoadPlan;
import com.example.scheduler.loadexecutor.domain.LoadShape;
import com.example.scheduler.loadexecutor.domain.LoadShapeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class DefaultLoadPlanner implements LoadPlanner {

    private final LoadExecutorProperties properties;

    @Override
    public LoadPlan plan(Command command) {
        LoadShape shape = command.getLoadShape();
        if (shape == null) {
            throw new PlanGenerationException("Missing loadShape in command");
        }
        List<LoadPhase> phases = switch (shape.getType()) {
            case HOT_KEY -> planHotKey(command, shape);
            case RAMP -> planRamp(command, shape);
            case BURST -> planBurst(command, shape);
            case CONSTANT -> planConstant(command, shape);
            default -> planConstant(command, shape);
        };
        return LoadPlan.builder()
                .experimentRunId(command.getExperimentRunId())
                .phases(phases)
                .build();
    }

    private List<LoadPhase> planConstant(Command command, LoadShape shape) {
        int concurrency = shape.resolveConcurrency(properties.getDefaultMaxConcurrency());
        LoadPhase phase = LoadPhase.builder()
                .index(0)
                .startOffset(Duration.ZERO)
                .endOffset(shape.getDuration())
                .targetQps(shape.getTargetQps())
                .maxConcurrency(concurrency)
                .description("constant")
                .build();
        return List.of(phase);
    }

    private List<LoadPhase> planHotKey(Command command, LoadShape shape) {
        int concurrency = shape.resolveConcurrency(properties.getDefaultMaxConcurrency());
        HotKeyConfig config = extractHotKeyConfig(shape.getParams());
        LoadPhase phase = LoadPhase.builder()
                .index(0)
                .startOffset(Duration.ZERO)
                .endOffset(shape.getDuration())
                .targetQps(shape.getTargetQps())
                .maxConcurrency(concurrency)
                .hotKeyConfig(config)
                .description("hot-key")
                .build();
        return List.of(phase);
    }

    private List<LoadPhase> planRamp(Command command, LoadShape shape) {
        Map<String, Object> params = shape.getParams();
        if (shape.getDuration() == null) {
            throw new PlanGenerationException("Ramp load requires durationSeconds");
        }
        int fromQps = intParam(params, "fromQps", shape.getTargetQps());
        int toQps = intParam(params, "toQps", shape.getTargetQps());
        long stepSeconds = longParam(params, "stepSeconds", 60);
        if (stepSeconds <= 0) {
            throw new PlanGenerationException("stepSeconds must be > 0");
        }
        Duration total = shape.getDuration();
        long steps = Math.max(1, total.getSeconds() / stepSeconds);
        double qpsDelta = (double) (toQps - fromQps) / steps;
        List<LoadPhase> phases = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            Duration start = Duration.ofSeconds(i * stepSeconds);
            Duration end = i == steps - 1 ? total : Duration.ofSeconds((i + 1) * stepSeconds);
            int qps = (int) Math.round(fromQps + qpsDelta * i);
            phases.add(LoadPhase.builder()
                    .index(i)
                    .startOffset(start)
                    .endOffset(end)
                    .targetQps(Math.max(1, qps))
                    .maxConcurrency(shape.resolveConcurrency(properties.getDefaultMaxConcurrency()))
                    .description("ramp-step-" + i)
                    .build());
        }
        return phases;
    }

    private List<LoadPhase> planBurst(Command command, LoadShape shape) {
        Map<String, Object> params = shape.getParams();
        Duration burstDuration = Duration.ofSeconds(longParam(params, "burstSeconds", 10));
        Duration cooldownDuration = Duration.ofSeconds(longParam(params, "cooldownSeconds", 20));
        int burstQps = intParam(params, "burstQps", shape.getTargetQps());
        int cooldownQps = intParam(params, "cooldownQps", Math.max(1, burstQps / 10));
        int repeat = intParam(params, "repeat", 1);
        if (repeat <= 0) {
            throw new PlanGenerationException("burst repeat must be >= 1");
        }
        List<LoadPhase> phases = new ArrayList<>();
        AtomicInteger index = new AtomicInteger();
        Duration cursor = Duration.ZERO;
        for (int i = 0; i < repeat; i++) {
            phases.add(LoadPhase.builder()
                    .index(index.getAndIncrement())
                    .startOffset(cursor)
                    .endOffset(cursor.plus(burstDuration))
                    .targetQps(burstQps)
                    .maxConcurrency(shape.resolveConcurrency(properties.getDefaultMaxConcurrency()))
                    .description("burst-" + i)
                    .build());
            cursor = cursor.plus(burstDuration);
            if (!cooldownDuration.isZero() && cooldownQps > 0) {
                phases.add(LoadPhase.builder()
                        .index(index.getAndIncrement())
                        .startOffset(cursor)
                        .endOffset(cursor.plus(cooldownDuration))
                        .targetQps(cooldownQps)
                        .maxConcurrency(shape.resolveConcurrency(properties.getDefaultMaxConcurrency()))
                        .description("cooldown-" + i)
                        .build());
                cursor = cursor.plus(cooldownDuration);
            }
        }
        return phases;
    }

    private HotKeyConfig extractHotKeyConfig(Map<String, Object> params) {
        if (params == null) {
            return HotKeyConfig.builder()
                    .hotKeyRatio(0.0)
                    .hotKeyCount(0)
                    .keySpaceSize(0)
                    .build();
        }
        return HotKeyConfig.builder()
                .hotKeyRatio(doubleParam(params, "hotKeyRatio", 0.0))
                .hotKeyCount(intParam(params, "hotKeyCount", 1))
                .keySpaceSize(longParam(params, "keySpaceSize", 1000))
                .keyPrefix(stringParam(params, "keyPrefix", "hot"))
                .build();
    }

    private int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params != null ? params.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            return Integer.parseInt(s);
        }
        return defaultValue;
    }

    private long longParam(Map<String, Object> params, String key, long defaultValue) {
        Object value = params != null ? params.get(key) : null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s) {
            return Long.parseLong(s);
        }
        return defaultValue;
    }

    private double doubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params != null ? params.get(key) : null;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            return Double.parseDouble(s);
        }
        return defaultValue;
    }

    private String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params != null ? params.get(key) : null;
        return value != null ? value.toString() : defaultValue;
    }
}
