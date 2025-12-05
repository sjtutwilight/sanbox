package com.example.scheduler.loadexecutor.planner;

public class PlanGenerationException extends RuntimeException {
    public PlanGenerationException(String message) {
        super(message);
    }

    public PlanGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
