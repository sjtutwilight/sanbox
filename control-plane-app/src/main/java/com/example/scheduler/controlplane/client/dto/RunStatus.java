package com.example.scheduler.controlplane.client.dto;

/**
 * Run status returned from load-executor.
 */
public enum RunStatus {
    INIT,
    RUNNING,
    PAUSED,
    STOPPED,
    COMPLETED,
    FAILED
}
