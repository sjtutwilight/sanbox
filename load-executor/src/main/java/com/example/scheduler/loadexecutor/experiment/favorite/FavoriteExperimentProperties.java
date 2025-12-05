package com.example.scheduler.loadexecutor.experiment.favorite;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "experiment.favorite")
public class FavoriteExperimentProperties {

    /**
     * Redis key prefix for cache entries.
     */
    private String keyPrefix = "fav";

    /**
     * Default TTL for cache-aside strategy.
     */
    private Duration cacheTtl = Duration.ofSeconds(30);
}
