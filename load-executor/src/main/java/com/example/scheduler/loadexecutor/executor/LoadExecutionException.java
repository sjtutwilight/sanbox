package com.example.scheduler.loadexecutor.executor;

public class LoadExecutionException extends RuntimeException {
    public LoadExecutionException(String message) {
        super(message);
    }

    public LoadExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
