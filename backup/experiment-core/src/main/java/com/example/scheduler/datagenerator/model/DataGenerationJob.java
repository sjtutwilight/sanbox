package com.example.scheduler.datagenerator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据生成任务状态
 */
@Getter
public class DataGenerationJob {
    private final String id;
    private final DataGenerationRequest requestSnapshot;
    private final AtomicLong written = new AtomicLong(0);
    private final AtomicLong failures = new AtomicLong(0);
    @Setter
    private long target;
    @Setter
    private DataGenerationStatus status = DataGenerationStatus.PENDING;
    @Setter
    private Instant startedAt;
    @Setter
    private Instant endedAt;
    @Setter
    private String lastError;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public DataGenerationJob(String id, DataGenerationRequest requestSnapshot) {
        this.id = id;
        this.requestSnapshot = requestSnapshot;
    }

    public long incrementWritten(long delta) {
        return written.addAndGet(delta);
    }

    public long incrementFailures(long delta) {
        return failures.addAndGet(delta);
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    @JsonIgnore
    public boolean isCancelled() {
        return cancelled.get();
    }
}
