package com.example.scheduler.loadexecutor.experiment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "experiment.dynamic-config")
public class ExperimentDynamicConfigProperties {

    /**
     * Toggle to enable/disable Nacos-backed overrides.
     */
    private boolean enabled = false;

    /**
     * Comma separated Nacos server list, e.g. localhost:8848.
     */
    private String serverAddr = "localhost:8848";

    /**
     * Optional Nacos namespace for experiment overrides.
     */
    private String namespace;

    /**
     * Nacos group name, default to EXPERIMENT.
     */
    private String group = "EXPERIMENT";

    /**
     * DataId that stores experiment overrides in JSON format.
     */
    private String dataId = "load-executor-experiment-params.json";

    /**
     * Timeout for pulling config during startup.
     */
    private long timeoutMs = 3000;

    /**
     * Optional username.
     */
    private String username;

    /**
     * Optional password.
     */
    private String password;
}
