package com.example.scheduler.experiment;

import lombok.Builder;
import lombok.Data;

/**
 * Parameter definition used by the front-end to render forms.
 */
@Data
@Builder
public class OperationParameterDefinition {
    private String name;
    private String label;
    private String type;
    private boolean required;
    private String description;
    private Object example;
    private Object defaultValue;
}
