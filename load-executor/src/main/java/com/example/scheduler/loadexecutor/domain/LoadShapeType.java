package com.example.scheduler.loadexecutor.domain;

public enum LoadShapeType {
    CONSTANT,
    HOT_KEY,
    RAMP,
    BURST;

    public static LoadShapeType fromString(String raw) {
        if (raw == null) {
            return CONSTANT;
        }
        return LoadShapeType.valueOf(raw.trim().toUpperCase());
    }
}
