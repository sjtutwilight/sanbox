package com.example.scheduler.loadexecutor.service;

import com.example.scheduler.experiment.OperationType;
import com.example.scheduler.loadexecutor.api.CommandRequest;
import com.example.scheduler.loadexecutor.api.LoadShapeRequest;
import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.ExperimentRun;
import com.example.scheduler.loadexecutor.domain.LoadPlan;
import com.example.scheduler.loadexecutor.domain.LoadShape;
import com.example.scheduler.loadexecutor.domain.LoadShapeType;
import com.example.scheduler.loadexecutor.domain.RunStatus;
import com.example.scheduler.loadexecutor.executor.LoadExecutor;
import com.example.scheduler.loadexecutor.executor.RunRepository;
import com.example.scheduler.loadexecutor.experiment.ExperimentOperationHandle;
import com.example.scheduler.loadexecutor.experiment.ExperimentRegistry;
import com.example.scheduler.loadexecutor.experiment.config.ExperimentDynamicConfigPublisher;
import com.example.scheduler.loadexecutor.planner.LoadPlanner;
import com.example.scheduler.loadexecutor.service.exception.CommandValidationException;
import com.example.scheduler.loadexecutor.service.exception.RunNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommandServiceImpl implements CommandService {

    private final ExperimentRegistry experimentRegistry;
    private final LoadPlanner loadPlanner;
    private final LoadExecutor loadExecutor;
    private final RunRepository runRepository;
    private final ExperimentDynamicConfigPublisher dynamicConfigPublisher;

    @Override
    public ExperimentRun submitCommand(CommandRequest request) {
        ExperimentOperationHandle handle = experimentRegistry.find(request.getExperimentId(), request.getGroupId(), request.getOperationId())
                .orElseThrow(() -> new CommandValidationException("Experiment operation not found"));
        String runId = StringUtils.hasText(request.getExperimentRunId()) ? request.getExperimentRunId() : UUID.randomUUID().toString();
        LoadShape loadShape = toLoadShape(request.getLoadShape());
        Command command = toCommand(request, runId, loadShape, handle);
        syncDynamicConfig(command, request.getOverrides());
        ExperimentRun run = ExperimentRun.builder()
                .id(runId)
                .command(command)
                .status(RunStatus.INIT)
                .createdAt(Instant.now())
                .build();
        runRepository.save(run);
        LoadPlan plan = loadPlanner.plan(command);
        loadExecutor.start(run, plan, handle);
        return loadExecutor.getRun(runId).orElse(run);
    }

    @Override
    public ExperimentRun stop(String experimentRunId) {
        loadExecutor.stop(experimentRunId);
        return getRun(experimentRunId);
    }

    @Override
    public ExperimentRun pause(String experimentRunId) {
        loadExecutor.pause(experimentRunId);
        return getRun(experimentRunId);
    }

    @Override
    public ExperimentRun resume(String experimentRunId) {
        loadExecutor.resume(experimentRunId);
        return getRun(experimentRunId);
    }

    @Override
    public ExperimentRun getRun(String experimentRunId) {
        return loadExecutor.getRun(experimentRunId)
                .orElseThrow(() -> new RunNotFoundException(experimentRunId));
    }

    @Override
    public List<ExperimentRun> listRuns() {
        return loadExecutor.getAllRuns();
    }

    private Command toCommand(CommandRequest request, String runId, LoadShape loadShape, ExperimentOperationHandle handle) {
        Command.CommandBuilder builder = Command.builder()
                .commandId(request.getCommandId())
                .experimentId(request.getExperimentId())
                .groupId(request.getGroupId())
                .operationId(request.getOperationId())
                .experimentRunId(runId)
                .operationType(resolveOperationType(request.getOperationType(), handle))
                .loadShape(loadShape);
        addMap(builder, request.getDataRequest(), true);
        addMap(builder, request.getOverrides(), false);
        return builder.build();
    }

    private LoadShape toLoadShape(LoadShapeRequest request) {
        LoadShapeType type;
        try {
            type = LoadShapeType.fromString(request.getType());
        } catch (IllegalArgumentException ex) {
            throw new CommandValidationException("Unsupported load shape type: " + request.getType());
        }
        LoadShape.LoadShapeBuilder builder = LoadShape.builder()
                .type(type)
                .targetQps(request.getQps())
                .maxConcurrency(request.getConcurrency())
                .duration(request.getDurationSeconds() != null ? Duration.ofSeconds(request.getDurationSeconds()) : null);
        if (request.getParams() != null) {
            builder.params(request.getParams());
        }
        return builder.build();
    }

    private OperationType resolveOperationType(OperationType requested, ExperimentOperationHandle handle) {
        if (requested != null) {
            return requested;
        }
        if (handle.getOperationType() != null) {
            return handle.getOperationType();
        }
        throw new CommandValidationException("Operation type is required");
    }

    private void addMap(Command.CommandBuilder builder, Map<String, Object> map, boolean data) {
        if (map == null || map.isEmpty()) {
            return;
        }
        if (data) {
            builder.dataRequest(map);
        } else {
            builder.overrides(map);
        }
    }

    private void syncDynamicConfig(Command command, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        dynamicConfigPublisher.publish(
                command.getExperimentId(),
                command.getGroupId(),
                command.getOperationId(),
                overrides);
    }
}
