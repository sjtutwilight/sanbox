package com.example.scheduler.loadexecutor.datasource.mysql;

public class MySqlOperationException extends RuntimeException {
    public MySqlOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
