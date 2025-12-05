package com.example.scheduler.loadexecutor.executor;

import com.example.scheduler.loadexecutor.domain.ExperimentRun;

import java.util.List;
import java.util.Optional;

public interface RunRepository {
    ExperimentRun save(ExperimentRun run);

    Optional<ExperimentRun> find(String experimentRunId);

    List<ExperimentRun> findAll();
}
