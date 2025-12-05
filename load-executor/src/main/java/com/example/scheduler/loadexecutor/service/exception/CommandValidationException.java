package com.example.scheduler.loadexecutor.service.exception;

public class CommandValidationException extends RuntimeException {
    public CommandValidationException(String message) {
        super(message);
    }
}
