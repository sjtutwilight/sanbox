package com.example.scheduler.loadexecutor.experiment.wallet;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "experiment.wallet")
public class WalletExperimentProperties {

    /**
     * Redis key prefix for wallet snapshots.
     */
    private String cacheKeyPrefix = "wallet";

    /**
     * Default TTL for cached snapshots.
     */
    private Duration defaultTtl = Duration.ofSeconds(30);
}
