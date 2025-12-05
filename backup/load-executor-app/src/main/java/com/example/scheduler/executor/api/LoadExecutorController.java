package com.example.scheduler.executor.api;

import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.executor.ExperimentCommandEnricher;
import com.example.scheduler.executor.LoadExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Unified command endpoint exposed to the control plane.
 */
@RestController
@RequiredArgsConstructor
public class LoadExecutorController {

    private final LoadExecutionService executionService;
    private final ExperimentCommandEnricher commandEnricher;

    @PostMapping("/commands")
    public LoadTask execute(@RequestBody ExecutorCommand command) {
        ExecutorCommand resolved = commandEnricher.enrich(command);
        return executionService.handle(resolved);
    }

    @PostMapping("/tasks/{taskId}/stop")
    public LoadTask stop(@PathVariable String taskId) {
        return executionService.stopTask(taskId);
    }

    @GetMapping("/tasks/{taskId}")
    public LoadTask getTask(@PathVariable String taskId) {
        return executionService.getTask(taskId);
    }

    @GetMapping("/tasks")
    public List<LoadTask> tasks() {
        return executionService.runningTasks();
    }
}
