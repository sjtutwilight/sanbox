package com.example.scheduler.loadexecutor.service.exception;

public class RunNotFoundException extends RuntimeException {
    public RunNotFoundException(String experimentRunId) {
        super("Run not found: " + experimentRunId);
    }
}
