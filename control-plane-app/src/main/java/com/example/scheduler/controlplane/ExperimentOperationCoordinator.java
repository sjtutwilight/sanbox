package com.example.scheduler.controlplane;

import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.controlplane.client.dto.LoadShapeRequest;
import com.example.scheduler.controller.dto.OperationExecutionRequest;
import com.example.scheduler.experiment.Experiment;
import com.example.scheduler.experiment.ExperimentProfileCatalog;
import com.example.scheduler.experiment.ExperimentService;
import com.example.scheduler.experiment.OperationProfileDefinition;
import com.example.scheduler.experiment.LoadTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责根据实验定义构造统一命令。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentOperationCoordinator {

    private final ExperimentService experimentService;
    private final ExperimentProfileCatalog profileCatalog;
    private final RunOrchestrator runOrchestrator;

    public LoadTask startOperation(String expId, String groupId, String opId,
                                   String experimentRunId, OperationExecutionRequest request) {
        Experiment.ExperimentOperation operation = experimentService.getOperation(expId, groupId, opId);
        OperationProfileDefinition selectedProfile = profileCatalog.validateProfile(
                toDescriptor(operation, opId),
                request.getPlatform(),
                request.getScenario());
        String taskId = buildTaskId(expId, groupId, opId);
        Map<String, Object> payload = request.getParameters() != null ? new HashMap<>(request.getParameters()) : new HashMap<>();
        // 将 profile 显式注入命令上下文，便于 executor 透传并让控制面在状态回查时恢复上下文。
        payload.put("platform", selectedProfile.getPlatform());
        payload.put("scenario", selectedProfile.getScenario());
        ExecutorCommand command = ExecutorCommand.builder()
                .groupId(groupId)
                .operationId(opId)
                .operationType(operation.getType())
                .overrides(payload)
                .build();
        return runOrchestrator.start(taskId, expId, experimentRunId, command, request.getLoadShape(), selectedProfile);
    }

    public LoadTask stopOperation(String expId, String groupId, String opId) {
        return runOrchestrator.stop(buildTaskId(expId, groupId, opId));
    }

    public LoadTask getStatus(String expId, String groupId, String opId) {
        return runOrchestrator.getTask(buildTaskId(expId, groupId, opId));
    }

    public List<LoadTask> getRunningTasks() {
        return runOrchestrator.getRunningTasks();
    }

    private String buildTaskId(String expId, String groupId, String opId) {
        return String.format("%s:%s:%s", expId, groupId, opId);
    }

    private com.example.scheduler.controlplane.client.dto.ExperimentOperationResponse toDescriptor(Experiment.ExperimentOperation operation, String operationId) {
        com.example.scheduler.controlplane.client.dto.ExperimentOperationResponse descriptor =
                new com.example.scheduler.controlplane.client.dto.ExperimentOperationResponse();
        descriptor.setOperationId(operationId);
        descriptor.setOperationType(operation.getType());
        return descriptor;
    }
}
