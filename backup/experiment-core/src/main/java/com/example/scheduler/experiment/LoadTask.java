package com.example.scheduler.experiment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 持续负载任务状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadTask {
    private String id;
    private String experimentId;
    /**
     * 单次实验运行ID，来自前端上下文
     */
    private String experimentRunId;
    private String groupId;
    private String operationId;
    private OperationType type;
    
    /**
     * 任务状态
     */
    private LoadTaskStatus status;
    
    /**
     * 开始时间
     */
    private Instant startedAt;
    
    /**
     * 结束时间
     */
    private Instant endedAt;
    
    /**
     * 已完成操作数
     */
    @Builder.Default
    @JsonIgnore
    private AtomicLong totalOps = new AtomicLong(0);
    
    /**
     * 当前 ops/s（滑动窗口计算）
     */
    private double currentOpsPerSec;
    
    /**
     * 平均延迟（毫秒）
     */
    private double avgLatencyMs;
    
    /**
     * 最大延迟（毫秒）
     */
    private long maxLatencyMs;
    
    /**
     * 错误计数
     */
    @Builder.Default
    @JsonIgnore
    private AtomicLong errors = new AtomicLong(0);
    
    /**
     * 最后错误信息
     */
    private String lastError;
    
    /**
     * 停止标志
     */
    @Builder.Default
    @JsonIgnore
    private AtomicBoolean stopRequested = new AtomicBoolean(false);
    
    /**
     * 用于初始化数据类型：目标数量
     */
    private long target;
    
    /**
     * 用于初始化数据类型：已写入数量
     */
    @Builder.Default
    @JsonIgnore
    private AtomicLong written = new AtomicLong(0);
    
    public void requestStop() {
        if (stopRequested != null) {
            stopRequested.set(true);
        }
    }
    
    public boolean shouldStop() {
        return stopRequested != null && stopRequested.get();
    }
    
    public void incrementOps() {
        if (totalOps != null) {
            totalOps.incrementAndGet();
        }
    }
    
    public void incrementOps(long delta) {
        if (totalOps != null) {
            totalOps.addAndGet(delta);
        }
    }
    
    public void incrementErrors() {
        if (errors != null) {
            errors.incrementAndGet();
        }
    }
    
    public void incrementWritten(long delta) {
        if (written != null) {
            written.addAndGet(delta);
        }
    }
    
    public long getWrittenValue() {
        return written != null ? written.get() : 0;
    }
    
    public long getTotalOpsValue() {
        return totalOps != null ? totalOps.get() : 0;
    }
    
    public long getErrorsValue() {
        return errors != null ? errors.get() : 0;
    }
    
    // JSON 序列化友好的 getter
    public long getWritten() {
        return getWrittenValue();
    }
    
    public long getOperations() {
        return getTotalOpsValue();
    }
    
    public long getErrorCount() {
        return getErrorsValue();
    }
}
