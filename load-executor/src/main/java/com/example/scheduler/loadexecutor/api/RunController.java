package com.example.scheduler.loadexecutor.api;

import com.example.scheduler.loadexecutor.domain.ExperimentRun;
import com.example.scheduler.loadexecutor.service.CommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/runs")
@RequiredArgsConstructor
public class RunController {

    private final CommandService commandService;
    private final RunResponseMapper mapper;

    @GetMapping
    public List<ExperimentRunResponse> list() {
        return commandService.listRuns().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{runId}")
    public ExperimentRunResponse get(@PathVariable String runId) {
        ExperimentRun run = commandService.getRun(runId);
        return mapper.toResponse(run);
    }
}
