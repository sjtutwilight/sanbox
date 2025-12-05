package com.example.scheduler.loadexecutor.experiment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnMissingBean(ExperimentDynamicConfigPublisher.class)
public class NoopExperimentDynamicConfigPublisher implements ExperimentDynamicConfigPublisher {

    @Override
    public void publish(String experimentId, String groupId, String operationId, Map<String, Object> overrides) {
        // no-op
    }
}
