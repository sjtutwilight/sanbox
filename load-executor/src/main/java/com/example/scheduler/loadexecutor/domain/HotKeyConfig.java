package com.example.scheduler.loadexecutor.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class HotKeyConfig {
    double hotKeyRatio;
    int hotKeyCount;
    long keySpaceSize;
    String keyPrefix;

    public double getClampedRatio() {
        if (hotKeyRatio <= 0) {
            return 0d;
        }
        if (hotKeyRatio >= 1) {
            return 1d;
        }
        return hotKeyRatio;
    }
}
