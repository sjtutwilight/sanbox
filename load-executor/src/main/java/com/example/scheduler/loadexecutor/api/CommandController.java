package com.example.scheduler.loadexecutor.api;

import com.example.scheduler.loadexecutor.domain.ExperimentRun;
import com.example.scheduler.loadexecutor.service.CommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/commands")
@RequiredArgsConstructor
public class CommandController {

    private final CommandService commandService;
    private final RunResponseMapper mapper;

    @PostMapping
    public ExperimentRunResponse submit(@Valid @RequestBody CommandRequest request) {
        ExperimentRun run = commandService.submitCommand(request);
        return mapper.toResponse(run);
    }

    @PostMapping("/{runId}/stop")
    public ExperimentRunResponse stop(@PathVariable String runId) {
        return mapper.toResponse(commandService.stop(runId));
    }

    @PostMapping("/{runId}/pause")
    public ExperimentRunResponse pause(@PathVariable String runId) {
        return mapper.toResponse(commandService.pause(runId));
    }

    @PostMapping("/{runId}/resume")
    public ExperimentRunResponse resume(@PathVariable String runId) {
        return mapper.toResponse(commandService.resume(runId));
    }
}
