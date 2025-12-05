package com.example.scheduler.controlplane;

import com.example.scheduler.controlplane.client.LoadExecutorClient;
import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.controlplane.client.dto.LoadShapeRequest;
import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.experiment.LoadTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 控制面运行调度器：负责 Run 级别的生命周期管理与 Load Executor 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunOrchestrator {

    private final LoadExecutorClient loadExecutorClient;
    private static final String LOAD_SHAPE_KEY = "__loadShape";

    /**
     * 记录最近一次运行 ID，便于查询和去重。
     */
    private final Map<String, String> lastRunByTask = new ConcurrentHashMap<>();

    public LoadTask start(String taskId, String experimentId, String experimentRunId,
                          ExecutorCommand command, LoadShapeRequest loadShape) {
        ensureNotRunning(taskId);
        ExecutorCommand enriched = command.toBuilder()
                .taskId(taskId)
                .experimentId(experimentId)
                .experimentRunId(resolveRunId(experimentRunId, experimentId))
                .overrides(enrichOverrides(command.getOverrides(), loadShape))
                .build();
        LoadTask task = loadExecutorClient.submit(enriched);
        registerRun(taskId, task);
        return task;
    }

    public LoadTask stop(String taskId) {
        String runId = resolveRunIdForTask(taskId);
        if (!StringUtils.hasText(runId)) {
            return idleTask(taskId);
        }
        LoadTask task = loadExecutorClient.stop(runId);
        registerRun(taskId, task);
        return task != null ? task : idleTask(taskId);
    }

    public LoadTask getTask(String taskId) {
        LoadTask task = fetchTask(taskId);
        if (task != null) {
            registerRun(taskId, task);
            return task;
        }
        return idleTask(taskId);
    }

    public List<LoadTask> getRunningTasks() {
        List<LoadTask> runs = loadExecutorClient.listRuns();
        runs.stream()
                .filter(task -> StringUtils.hasText(task.getId()))
                .forEach(task -> lastRunByTask.put(task.getId(), task.getExperimentRunId()));
        return runs.stream()
                .filter(task -> task.getStatus() == LoadTaskStatus.RUNNING)
                .toList();
    }

    private void ensureNotRunning(String taskId) {
        LoadTask existing = fetchTask(taskId);
        if (existing != null && existing.getStatus() == LoadTaskStatus.RUNNING) {
            throw new IllegalStateException("该操作已有任务在运行中");
        }
    }

    private void registerRun(String taskId, LoadTask task) {
        if (task != null) {
            lastRunByTask.put(taskId, task.getExperimentRunId());
        }
    }

    private LoadTask fetchTask(String taskId) {
        String runId = resolveRunIdForTask(taskId);
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        return loadExecutorClient.getRun(runId);
    }

    private String resolveRunIdForTask(String taskId) {
        String runId = lastRunByTask.get(taskId);
        if (StringUtils.hasText(runId)) {
            return runId;
        }
        List<LoadTask> runs = loadExecutorClient.listRuns();
        Optional<LoadTask> latest = runs.stream()
                .filter(task -> taskId.equals(task.getId()))
                .max(Comparator.comparing(task -> Optional.ofNullable(task.getStartedAt()).orElse(Instant.EPOCH)));
        latest.ifPresent(task -> lastRunByTask.put(taskId, task.getExperimentRunId()));
        return latest.map(LoadTask::getExperimentRunId).orElse(null);
    }

    private LoadTask idleTask(String taskId) {
        return LoadTask.builder()
                .id(taskId)
                .status(LoadTaskStatus.IDLE)
                .build();
    }

    private String resolveRunId(String provided, String experimentId) {
        if (StringUtils.hasText(provided)) {
            return provided;
        }
        String prefix = StringUtils.hasText(experimentId) ? experimentId : "exp";
        return prefix + "-" + Instant.now().toEpochMilli();
    }

    private Map<String, Object> enrichOverrides(Map<String, Object> overrides, LoadShapeRequest loadShape) {
        Map<String, Object> payload = overrides != null ? new HashMap<>(overrides) : new HashMap<>();
        if (loadShape != null) {
            Map<String, Object> shape = new LinkedHashMap<>();
            shape.put("type", loadShape.getType());
            shape.put("qps", loadShape.getQps());
            shape.put("concurrency", loadShape.getConcurrency());
            shape.put("durationSeconds", loadShape.getDurationSeconds());
            shape.put("params", loadShape.getParams());
            payload.put(LOAD_SHAPE_KEY, shape);
        }
        return payload;
    }
}
