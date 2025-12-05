package com.example.scheduler.controlplane.client.dto;

import lombok.Data;

/**
 * Parameter metadata definition from Load Executor.
 */
@Data
public class OperationParameterResponse {
    private String name;
    private String label;
    private String type;
    private boolean required;
    private String description;
    private Object example;
    private Object defaultValue;
}
