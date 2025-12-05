package com.example.scheduler.controlplane.client;

import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.controlplane.client.dto.ExperimentDescriptorResponse;
import com.example.scheduler.experiment.LoadTask;

import java.util.List;

/**
 * 控制面调用的 Load Executor 抽象。
 */
public interface LoadExecutorClient {

    /**
     * 提交统一命令。
     */
    LoadTask submit(ExecutorCommand command);

    /**
     * 停止指定运行。
     */
    LoadTask stop(String experimentRunId);

    /**
     * 根据运行ID获取状态，若不存在则返回 null。
     */
    LoadTask getRun(String experimentRunId);

    /**
     * 获取所有已知运行（可由调用方自行过滤）。
     */
    List<LoadTask> listRuns();

    /**
     * 获取 Load Executor 暴露的实验元数据。
     */
    List<ExperimentDescriptorResponse> listExperiments();
}
