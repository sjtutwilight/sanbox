package com.example.scheduler.controlplane.client.command;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.experiment.CacheExperimentConfig;
import com.example.scheduler.experiment.Experiment;
import com.example.scheduler.experiment.OperationType;
import com.example.scheduler.experiment.scenario.ScenarioParams;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Unified command sent from control plane to Load Executor.
 */
@Data
@Builder(toBuilder = true)
public class ExecutorCommand {

    private String taskId;
    private String experimentId;
    private String groupId;
    private String operationId;
    private String experimentRunId;

    /**
     * Operation type resolved from experiment definitions.
     */
    private OperationType operationType;

    /**
     * Dynamic overrides from front-end or control plane.
     */
    private Map<String, Object> overrides;

    /**
     * Data generation payload for init/write workloads.
     */
    private DataGenerationRequest dataRequest;

    /**
     * Read workload payload.
     */
    private Experiment.ReadLoadConfig readConfig;

    /**
     * Cache experiment init configs.
     */
    private CacheExperimentConfig.MysqlInitConfig mysqlInitConfig;
    private CacheExperimentConfig.RedisInitConfig redisInitConfig;

    /**
     * Scenario parameters when the experiment defines special workloads.
     */
    private ScenarioParams scenarioParams;
}
