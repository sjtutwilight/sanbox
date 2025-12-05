package com.example.scheduler.loadexecutor.experiment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OperationParameter {
    String name;
    String label;
    String type;
    boolean required;
    String description;
    Object example;
    Object defaultValue;
}
