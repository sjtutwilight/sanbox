package com.example.scheduler.loadexecutor.api;

import com.example.scheduler.loadexecutor.experiment.ExperimentDescriptor;
import com.example.scheduler.loadexecutor.experiment.ExperimentRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/experiments")
@RequiredArgsConstructor
public class ExperimentMetadataController {

    private final ExperimentRegistry experimentRegistry;

    @GetMapping
    public Collection<ExperimentDescriptor> list() {
        return experimentRegistry.descriptors();
    }
}
