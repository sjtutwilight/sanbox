package com.example.scheduler.loadexecutor.experiment.config;

import com.example.scheduler.loadexecutor.domain.Command;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
@ConditionalOnMissingBean(ExperimentParameterOverrideService.class)
public class NoopExperimentParameterOverrideService implements ExperimentParameterOverrideService {

    @Override
    public Map<String, Object> currentParameters(Command command) {
        return Collections.emptyMap();
    }
}
