package com.example.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 观测性相关配置（Grafana/Loki等）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private LokiConfig loki = new LokiConfig();
    private GrafanaConfig grafana = new GrafanaConfig();

    @Data
    public static class LokiConfig {
        private String url = "http://localhost:3100";
    }

    @Data
    public static class GrafanaConfig {
        private String url = "http://localhost:3000";
        private String orgId = "1";
        private String defaultRefresh = "5s";
        private List<DashboardConfig> dashboards = new ArrayList<>();
    }

    @Data
    public static class DashboardConfig {
        private String uid;
        private String title;
        private String description;
        private String defaultFrom = "now-15m";
        private String defaultTo = "now";
        private String height = "600px";
    }
}
