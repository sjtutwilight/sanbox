package com.example.scheduler.loadexecutor.datasource.postgres;

public class PostgresOperationException extends RuntimeException {
    public PostgresOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
