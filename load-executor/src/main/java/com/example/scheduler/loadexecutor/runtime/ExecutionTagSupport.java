package com.example.scheduler.loadexecutor.runtime;

/**
 * 统一生成运行时指标标签，避免各组件重复拼接 profile 维度。
 */
public final class ExecutionTagSupport {

    private ExecutionTagSupport() {
    }

    /**
     * 当前运行的 platform 标签。
     */
    public static String platform() {
        RunExecutionContext context = RunExecutionContextHolder.current();
        return context != null && context.getPlatform() != null ? context.getPlatform() : "unknown";
    }

    /**
     * 当前运行的 scenario 标签。
     */
    public static String scenario() {
        RunExecutionContext context = RunExecutionContextHolder.current();
        return context != null && context.getScenario() != null ? context.getScenario() : "unknown";
    }

    /**
     * 当前运行的 experimentRunId 标签。
     */
    public static String experimentRunId() {
        RunExecutionContext context = RunExecutionContextHolder.current();
        return context != null && context.getExperimentRunId() != null ? context.getExperimentRunId() : "unknown";
    }

    /**
     * 当前运行的 Redis 策略标签。
     */
    public static String redisStrategy() {
        RunExecutionContext context = RunExecutionContextHolder.current();
        return context != null && context.getRedisStrategy() != null ? context.getRedisStrategy().name().toLowerCase() : "unknown";
    }
}
