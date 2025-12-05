package com.example.scheduler.loadexecutor.service;

import com.example.scheduler.loadexecutor.api.CommandRequest;
import com.example.scheduler.loadexecutor.domain.ExperimentRun;

import java.util.List;

public interface CommandService {

    ExperimentRun submitCommand(CommandRequest request);

    ExperimentRun stop(String experimentRunId);

    ExperimentRun pause(String experimentRunId);

    ExperimentRun resume(String experimentRunId);

    ExperimentRun getRun(String experimentRunId);

    List<ExperimentRun> listRuns();
}
