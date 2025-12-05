package com.example.scheduler.loadexecutor.experiment;

import java.util.List;

public interface ExperimentGroup {

    String experimentId();

    default String experimentName() {
        return experimentId();
    }

    default String groupId() {
        return "default";
    }

    default String groupName() {
        return groupId();
    }

    default String groupDescription() {
        return groupName();
    }

    List<ExperimentOperationDefinition> operations();
}
