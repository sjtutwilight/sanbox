package com.example.scheduler.loadexecutor.experiment.config;

import com.example.scheduler.loadexecutor.domain.Command;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;

@Configuration
public class ExperimentDynamicConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExperimentParameterOverrideService noopExperimentParameterOverrideService() {
        return new NoopOverrideService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExperimentDynamicConfigPublisher noopExperimentDynamicConfigPublisher() {
        return new NoopPublisher();
    }

    private static class NoopOverrideService implements ExperimentParameterOverrideService {
        @Override
        public Map<String, Object> currentParameters(Command command) {
            return Collections.emptyMap();
        }
    }

    private static class NoopPublisher implements ExperimentDynamicConfigPublisher {
        @Override
        public void publish(String experimentId, String groupId, String operationId, Map<String, Object> overrides) {
            // no-op
        }
    }
}
