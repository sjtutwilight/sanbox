package com.example.scheduler.loadexecutor.executor;

import com.example.scheduler.loadexecutor.domain.ExperimentRun;
import com.example.scheduler.loadexecutor.domain.LoadPlan;
import com.example.scheduler.loadexecutor.experiment.ExperimentOperationHandle;

import java.util.List;
import java.util.Optional;

public interface LoadExecutor {

    void start(ExperimentRun run, LoadPlan plan, ExperimentOperationHandle handle);

    void stop(String experimentRunId);

    void pause(String experimentRunId);

    void resume(String experimentRunId);

    Optional<ExperimentRun> getRun(String experimentRunId);

    List<ExperimentRun> getAllRuns();
}
