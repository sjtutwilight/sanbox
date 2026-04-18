package com.example.scheduler.loadexecutor.runtime;

/**
 * 运行上下文持有器，供数据源与观测组件从当前线程读取 profile。
 */
public final class RunExecutionContextHolder {

    private static final ThreadLocal<RunExecutionContext> CURRENT = new ThreadLocal<>();

    private RunExecutionContextHolder() {
    }

    /**
     * 写入当前线程的执行上下文。
     */
    public static void set(RunExecutionContext context) {
        CURRENT.set(context);
    }

    /**
     * 读取当前线程的执行上下文。
     */
    public static RunExecutionContext current() {
        return CURRENT.get();
    }

    /**
     * 清理当前线程上下文，避免线程池复用时串上下文。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
