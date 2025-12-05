package com.example.scheduler.executor;

import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.experiment.CacheExperimentConfig;
import com.example.scheduler.experiment.ContinuousLoadService;
import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.experiment.LoadTaskStatus;
import com.example.scheduler.experiment.OperationType;
import com.example.scheduler.service.MysqlDataInitService;
import com.example.scheduler.service.RedisDataInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 执行层：根据控制面下发的统一命令启动压测任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadExecutionService {

    private final ContinuousLoadService continuousLoadService;
    private final MysqlDataInitService mysqlDataInitService;
    private final RedisDataInitService redisDataInitService;

    private final ExecutorService initExecutor = Executors.newCachedThreadPool();

    public LoadTask handle(ExecutorCommand command) {
        OperationType type = command.getOperationType();
        if (type == null) {
            throw new IllegalArgumentException("operationType is required");
        }
        return switch (type) {
            case INIT_MYSQL -> startMysqlInit(command);
            case INIT_REDIS -> startRedisInit(command);
            case INIT_DATA -> startInitData(command);
            case CONTINUOUS_WRITE -> startWriteLoad(command);
            case CONTINUOUS_READ -> startReadLoad(command);
        };
    }

    private LoadTask startInitData(ExecutorCommand command) {
        requireDataRequest(command);
        return continuousLoadService.startInitData(
                command.getExperimentId(),
                command.getGroupId(),
                command.getOperationId(),
                command.getDataRequest(),
                command.getExperimentRunId());
    }

    private LoadTask startWriteLoad(ExecutorCommand command) {
        requireDataRequest(command);
        return continuousLoadService.startWriteLoad(
                command.getExperimentId(),
                command.getGroupId(),
                command.getOperationId(),
                command.getDataRequest(),
                command.getExperimentRunId());
    }

    private LoadTask startReadLoad(ExecutorCommand command) {
        if (command.getReadConfig() == null) {
            throw new IllegalArgumentException("readConfig is required");
        }
        return continuousLoadService.startReadLoad(
                command.getExperimentId(),
                command.getGroupId(),
                command.getOperationId(),
                command.getReadConfig(),
                command.getExperimentRunId());
    }

    public LoadTask stopTask(String taskId) {
        String[] parts = parseTaskId(taskId);
        return continuousLoadService.stopTask(parts[0], parts[1], parts[2]);
    }

    public LoadTask getTask(String taskId) {
        String[] parts = parseTaskId(taskId);
        return continuousLoadService.getTaskStatus(parts[0], parts[1], parts[2]);
    }

    public List<LoadTask> runningTasks() {
        return continuousLoadService.getRunningTasks();
    }

    private LoadTask startMysqlInit(ExecutorCommand command) {
        CacheExperimentConfig.MysqlInitConfig cfg = command.getMysqlInitConfig();
        if (cfg == null) {
            throw new IllegalArgumentException("mysql init config is required");
        }
        String runId = resolveRunId(command);

        LoadTask task = LoadTask.builder()
                .id(command.getTaskId())
                .experimentId(command.getExperimentId())
                .experimentRunId(runId)
                .groupId(command.getGroupId())
                .operationId(command.getOperationId())
                .type(OperationType.INIT_MYSQL)
                .status(LoadTaskStatus.IDLE)
                .startedAt(Instant.now())
                .target(cfg.getUserCount() * cfg.getFavPerUser())
                .build();

        continuousLoadService.registerTask(task);
        initExecutor.submit(() -> runMysqlInit(task, cfg));
        return task;
    }

    private LoadTask startRedisInit(ExecutorCommand command) {
        CacheExperimentConfig.RedisInitConfig cfg = command.getRedisInitConfig();
        if (cfg == null) {
            throw new IllegalArgumentException("redis init config is required");
        }
        String runId = resolveRunId(command);

        LoadTask task = LoadTask.builder()
                .id(command.getTaskId())
                .experimentId(command.getExperimentId())
                .experimentRunId(runId)
                .groupId(command.getGroupId())
                .operationId(command.getOperationId())
                .type(OperationType.INIT_REDIS)
                .status(LoadTaskStatus.IDLE)
                .startedAt(Instant.now())
                .target(cfg.getUserCount())
                .build();

        continuousLoadService.registerTask(task);
        initExecutor.submit(() -> runRedisInit(task, cfg));
        return task;
    }

    private void runMysqlInit(LoadTask task, CacheExperimentConfig.MysqlInitConfig cfg) {
        try {
            task.setStatus(LoadTaskStatus.RUNNING);
            long written = mysqlDataInitService.initFavorites(cfg);
            task.incrementWritten(written);
            task.setStatus(LoadTaskStatus.COMPLETED);
            task.setEndedAt(Instant.now());
        } catch (Exception e) {
            log.error("MySQL初始化失败：{}", task.getId(), e);
            task.setStatus(LoadTaskStatus.FAILED);
            task.setLastError(e.getMessage());
            task.setEndedAt(Instant.now());
        }
    }

    private void runRedisInit(LoadTask task, CacheExperimentConfig.RedisInitConfig cfg) {
        try {
            task.setStatus(LoadTaskStatus.RUNNING);
            long written = redisDataInitService.initFavorites(cfg);
            task.incrementWritten(written);
            task.setStatus(LoadTaskStatus.COMPLETED);
            task.setEndedAt(Instant.now());
        } catch (Exception e) {
            log.error("Redis初始化失败：{}", task.getId(), e);
            task.setStatus(LoadTaskStatus.FAILED);
            task.setLastError(e.getMessage());
            task.setEndedAt(Instant.now());
        }
    }

    private String resolveRunId(ExecutorCommand command) {
        if (command.getExperimentRunId() != null) {
            return command.getExperimentRunId();
        }
        return command.getTaskId() + ":" + UUID.randomUUID();
    }

    private String[] parseTaskId(String taskId) {
        String[] parts = taskId.split(":", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("invalid taskId: " + taskId);
        }
        return parts;
    }

    private void requireDataRequest(ExecutorCommand command) {
        if (command.getDataRequest() == null) {
            throw new IllegalArgumentException("dataRequest is required");
        }
    }
}
