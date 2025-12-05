package com.example.scheduler.loadexecutor.experiment.config;

import java.util.Map;

public interface ExperimentDynamicConfigPublisher {

    void publish(String experimentId, String groupId, String operationId, Map<String, Object> overrides);
}
