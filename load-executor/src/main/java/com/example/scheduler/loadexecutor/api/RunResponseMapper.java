package com.example.scheduler.loadexecutor.api;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.ExperimentRun;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RunResponseMapper {

    public ExperimentRunResponse toResponse(ExperimentRun run) {
        if (run == null) {
            return null;
        }
        Command cmd = run.getCommand();
        return ExperimentRunResponse.builder()
                .experimentRunId(run.getId())
                .experimentId(cmd != null ? cmd.getExperimentId() : null)
                .groupId(cmd != null ? cmd.getGroupId() : null)
                .operationId(cmd != null ? cmd.getOperationId() : null)
                .status(run.getStatus())
                .createdAt(run.getCreatedAt())
                .startedAt(run.getStartedAt())
                .endedAt(run.getEndedAt())
                .lastError(run.getLastError())
                .command(extractCommand(cmd))
                .metrics(run.getMetrics())
                .build();
    }

    private Map<String, Object> extractCommand(Command command) {
        if (command == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("commandId", command.getCommandId());
        map.put("experimentId", command.getExperimentId());
        map.put("groupId", command.getGroupId());
        map.put("operationId", command.getOperationId());
        map.put("experimentRunId", command.getExperimentRunId());
        map.put("operationType", command.getOperationType());
        map.put("loadShape", command.getLoadShape());
        map.put("dataRequest", command.getDataRequest());
        map.put("overrides", command.getOverrides());
        return map;
    }
}
