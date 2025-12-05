package com.example.scheduler.loadexecutor.experiment;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExperimentDescriptor {
    String experimentId;
    String groupId;
    String experimentName;
    String groupName;
    String groupDescription;
    String description;
    List<ExperimentOperationDefinition> operations;
}
