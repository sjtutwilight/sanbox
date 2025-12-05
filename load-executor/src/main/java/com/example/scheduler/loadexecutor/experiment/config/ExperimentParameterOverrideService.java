package com.example.scheduler.loadexecutor.experiment.config;

import com.example.scheduler.loadexecutor.domain.Command;

import java.util.Collections;
import java.util.Map;

public interface ExperimentParameterOverrideService {

    /**
     * Return runtime parameter overrides for a given command.
     */
    Map<String, Object> currentParameters(Command command);

    /**
     * Utility method for no-op implementations.
     */
    default Map<String, Object> empty() {
        return Collections.emptyMap();
    }
}
