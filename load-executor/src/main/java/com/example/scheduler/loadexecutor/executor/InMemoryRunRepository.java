package com.example.scheduler.loadexecutor.executor;

import com.example.scheduler.loadexecutor.domain.ExperimentRun;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryRunRepository implements RunRepository {

    private final ConcurrentMap<String, ExperimentRun> store = new ConcurrentHashMap<>();

    @Override
    public ExperimentRun save(ExperimentRun run) {
        store.put(run.getId(), run);
        return run;
    }

    @Override
    public Optional<ExperimentRun> find(String experimentRunId) {
        return Optional.ofNullable(store.get(experimentRunId));
    }

    @Override
    public List<ExperimentRun> findAll() {
        return new ArrayList<>(store.values());
    }
}
