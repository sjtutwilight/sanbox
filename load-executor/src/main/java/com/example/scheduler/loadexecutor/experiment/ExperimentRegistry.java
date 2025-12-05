package com.example.scheduler.loadexecutor.experiment;

import java.util.Collection;
import java.util.Optional;

public interface ExperimentRegistry {

    Optional<ExperimentOperationHandle> find(String experimentId, String groupId, String operationId);

    Collection<ExperimentDescriptor> descriptors();
}
