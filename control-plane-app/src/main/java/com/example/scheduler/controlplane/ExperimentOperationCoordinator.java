package com.example.scheduler.controlplane;

import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.controlplane.client.dto.LoadShapeRequest;
import com.example.scheduler.experiment.Experiment;
import com.example.scheduler.experiment.ExperimentService;
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
    private final RunOrchestrator runOrchestrator;

    public LoadTask startOperation(String expId, String groupId, String opId,
                                   String experimentRunId, Map<String, Object> overrides,
                                   LoadShapeRequest loadShape) {
        Experiment.ExperimentOperation operation = experimentService.getOperation(expId, groupId, opId);
        String taskId = buildTaskId(expId, groupId, opId);
        Map<String, Object> payload = overrides != null ? new HashMap<>(overrides) : Map.of();
        ExecutorCommand command = ExecutorCommand.builder()
                .groupId(groupId)
                .operationId(opId)
                .operationType(operation.getType())
                .overrides(payload)
                .build();
        return runOrchestrator.start(taskId, expId, experimentRunId, command, loadShape);
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
}
