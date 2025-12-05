package com.example.scheduler.controlplane.client.dto;

import lombok.Data;

import java.util.List;

/**
 * Experiment metadata descriptor returned by Load Executor.
 */
@Data
public class ExperimentDescriptorResponse {
    private String experimentId;
    private String experimentName;
    private String experimentDescription;
    private String groupId;
    private String groupName;
    private String groupDescription;
    private List<ExperimentOperationResponse> operations;
}
