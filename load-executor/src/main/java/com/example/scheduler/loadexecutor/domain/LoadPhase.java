package com.example.scheduler.loadexecutor.domain;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

@Value
@Builder(toBuilder = true)
public class LoadPhase {
    int index;
    Duration startOffset;
    Duration endOffset;
    int targetQps;
    int maxConcurrency;
    HotKeyConfig hotKeyConfig;
    String description;

    public boolean contains(Duration elapsed) {
        if (elapsed.isNegative()) {
            return false;
        }
        boolean afterStart = !elapsed.minus(startOffset).isNegative();
        if (!afterStart) {
            return false;
        }
        if (endOffset == null) {
            return true;
        }
        return elapsed.compareTo(endOffset) < 0;
    }
}
