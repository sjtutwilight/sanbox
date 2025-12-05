package com.example.scheduler.experiment;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datagenerator.support.RequestDefaultsResolver;
import com.example.scheduler.core.load.InitDataEngine;
import com.example.scheduler.core.load.WriteLoadEngine;
import com.example.scheduler.core.load.ReadLoadEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.util.StringUtils;
import org.slf4j.MDC;

/**
 * 持续负载服务：管理持续运行的读写压测任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContinuousLoadService {

    private final RequestDefaultsResolver requestDefaultsResolver;
    private final InitDataEngine initDataEngine;
    private final WriteLoadEngine writeLoadEngine;
    private final ReadLoadEngine readLoadEngine;

    /**
     * 运行中的任务
     */
    private final Map<String, LoadTask> runningTasks = new ConcurrentHashMap<>();

    /**
     * 任务历史（包含已完成的任务）
     */
    private final Map<String, LoadTask> taskHistory = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 启动初始化数据任务
     */
    public LoadTask startInitData(String experimentId, String groupId, String operationId,
                                   DataGenerationRequest request, String experimentRunId) {
        String taskId = buildTaskId(experimentId, groupId, operationId);
        
        // 检查是否已有运行中的任务
        if (runningTasks.containsKey(taskId)) {
            throw new IllegalStateException("该操作已有任务在运行中");
        }
        
        applyDefaults(request);
        String runId = resolveExperimentRunId(experimentRunId, experimentId);
        
        LoadTask task = LoadTask.builder()
                .id(taskId)
                .experimentId(experimentId)
                .experimentRunId(runId)
                .groupId(groupId)
                .operationId(operationId)
                .type(OperationType.INIT_DATA)
                .status(LoadTaskStatus.RUNNING)
                .startedAt(Instant.now())
                .target(request.getRecordCount())
                .build();
        
        runningTasks.put(taskId, task);
        taskHistory.put(taskId, task);
        log.info("启动初始化数据 recordCount={} batchSize={} keyPrefix={} experimentRunId={}", 
                request.getRecordCount(), request.getBatchSize(), request.getKeyPrefix(), runId);
        
        executor.submit(() -> runInitDataTask(task, request));
        
        return task;
    }

    /**
     * 启动持续写入负载
     */
    public LoadTask startWriteLoad(String experimentId, String groupId, String operationId,
                                    DataGenerationRequest request, String experimentRunId) {
        String taskId = buildTaskId(experimentId, groupId, operationId);
        
        if (runningTasks.containsKey(taskId)) {
            throw new IllegalStateException("该操作已有任务在运行中");
        }
        
        applyDefaults(request);
        String runId = resolveExperimentRunId(experimentRunId, experimentId);
        
        LoadTask task = LoadTask.builder()
                .id(taskId)
                .experimentId(experimentId)
                .experimentRunId(runId)
                .groupId(groupId)
                .operationId(operationId)
                .type(OperationType.CONTINUOUS_WRITE)
                .status(LoadTaskStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        
        runningTasks.put(taskId, task);
        taskHistory.put(taskId, task);
        log.info("启动持续写入 batchSize={} qps={} writeThrough={} keyPrefix={} experimentRunId={}",
                request.getBatchSize(), request.getQps(), request.getWriteThrough(), request.getKeyPrefix(), runId);
        
        executor.submit(() -> runContinuousWriteTask(task, request));
        
        return task;
    }

    /**
     * 启动持续读取负载
     */
    public LoadTask startReadLoad(String experimentId, String groupId, String operationId,
                                   Experiment.ReadLoadConfig config, String experimentRunId) {
        String taskId = buildTaskId(experimentId, groupId, operationId);
        
        if (runningTasks.containsKey(taskId)) {
            throw new IllegalStateException("该操作已有任务在运行中");
        }
        
        String runId = resolveExperimentRunId(experimentRunId, experimentId);

        LoadTask task = LoadTask.builder()
                .id(taskId)
                .experimentId(experimentId)
                .experimentRunId(runId)
                .groupId(groupId)
                .operationId(operationId)
                .type(OperationType.CONTINUOUS_READ)
                .status(LoadTaskStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        
        runningTasks.put(taskId, task);
        taskHistory.put(taskId, task);
        log.info("启动持续读取 qps={} concurrency={} cacheStrategy={} distribution={} hotShare={} experimentRunId={}",
                config.getQps(), config.getConcurrency(), config.getCacheStrategy(), config.getIdDistribution(), config.getHotShare(), runId);
        
        executor.submit(() -> runContinuousReadTask(task, config));
        
        return task;
    }

    /**
     * 停止任务
     */
    public LoadTask stopTask(String experimentId, String groupId, String operationId) {
        String taskId = buildTaskId(experimentId, groupId, operationId);
        LoadTask task = runningTasks.get(taskId);
        
        if (task == null) {
            task = taskHistory.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("任务不存在: " + taskId);
            }
            return task;
        }
        
        task.requestStop();
        return task;
    }

    /**
     * 获取任务状态
     */
    public LoadTask getTaskStatus(String experimentId, String groupId, String operationId) {
        String taskId = buildTaskId(experimentId, groupId, operationId);
        LoadTask task = runningTasks.get(taskId);
        if (task == null) {
            task = taskHistory.get(taskId);
        }
        return task;
    }

    /**
     * 获取所有运行中的任务
     */
    public List<LoadTask> getRunningTasks() {
        return new ArrayList<>(runningTasks.values());
    }
    
    /**
     * 注册外部任务（用于MySQL/Redis初始化等）
     */
    public void registerTask(LoadTask task) {
        String taskId = buildTaskId(task.getExperimentId(), task.getGroupId(), task.getOperationId());
        if (!StringUtils.hasText(task.getExperimentRunId())) {
            task.setExperimentRunId(resolveExperimentRunId(null, task.getExperimentId()));
        }
        runningTasks.put(taskId, task);
        taskHistory.put(taskId, task);
    }

    private String resolveExperimentRunId(String experimentRunId, String experimentId) {
        if (StringUtils.hasText(experimentRunId)) {
            return experimentRunId;
        }
        return experimentId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void bindMdc(LoadTask task) {
        if (task == null) {
            return;
        }
        if (StringUtils.hasText(task.getExperimentRunId())) {
            MDC.put("experimentId", task.getExperimentRunId());
        }
        if (StringUtils.hasText(task.getExperimentId())) {
            MDC.put("experimentTemplateId", task.getExperimentId());
        }
        if (StringUtils.hasText(task.getGroupId())) {
            MDC.put("groupId", task.getGroupId());
        }
        if (StringUtils.hasText(task.getOperationId())) {
            MDC.put("operationId", task.getOperationId());
        }
        if (StringUtils.hasText(task.getId())) {
            MDC.put("taskId", task.getId());
        }
    }

    private void clearMdc() {
        MDC.remove("experimentId");
        MDC.remove("experimentTemplateId");
        MDC.remove("groupId");
        MDC.remove("operationId");
        MDC.remove("taskId");
    }

    private String buildTaskId(String experimentId, String groupId, String operationId) {
        return String.format("%s:%s:%s", experimentId, groupId, operationId);
    }

    private void runInitDataTask(LoadTask task, DataGenerationRequest request) {
        bindMdc(task);
        try {
            initDataEngine.run(task, request);
        } finally {
            runningTasks.remove(task.getId());
            clearMdc();
        }
    }

    private void runContinuousWriteTask(LoadTask task, DataGenerationRequest request) {
        bindMdc(task);
        try {
            writeLoadEngine.run(task, request);
        } finally {
            runningTasks.remove(task.getId());
            clearMdc();
        }
    }

    private void runContinuousReadTask(LoadTask task, Experiment.ReadLoadConfig config) {
        bindMdc(task);
        try {
            readLoadEngine.run(task, config);
        } finally {
            runningTasks.remove(task.getId());
            clearMdc();
        }
    }

    private void applyDefaults(DataGenerationRequest request) {
        requestDefaultsResolver.applyDefaults(request);
    }

}
