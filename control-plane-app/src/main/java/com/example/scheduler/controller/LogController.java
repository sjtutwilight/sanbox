package com.example.scheduler.controller;

import com.example.scheduler.config.ObservabilityProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loki 日志查询代理，前端可直接通过 experimentId 拉取背景日志
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final RestTemplate restTemplate;
    private final String lokiBaseUrl;

    public LogController(ObservabilityProperties observabilityProperties,
                         RestTemplateBuilder builder) {
        this.lokiBaseUrl = observabilityProperties.getLoki().getUrl();
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    @GetMapping
    public LogSummary fetchLogs(@RequestParam("experimentId") String experimentId,
                                @RequestParam(value = "limit", defaultValue = "200") int limit,
                                @RequestParam(value = "rangeSeconds", defaultValue = "900") long rangeSeconds) {
        if (!StringUtils.hasText(experimentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experimentId is required");
        }
        Instant end = Instant.now();
        Instant start = end.minusSeconds(Math.max(rangeSeconds, 60));

        List<LogEntry> appLogs = queryLoki("{job=\"application\",experiment_id=\"" + experimentId + "\"}", start, end, limit);
        List<LogEntry> dockerLogs = queryLoki("{job=\"docker\",container=~\"scheduler-(mysql|redis).*\"} |~ \"(?i)error\"", start, end, 200);
        List<ContainerLog> containerErrors = aggregateContainerErrors(dockerLogs);

        return LogSummary.builder()
                .appLogs(appLogs)
                .containerErrors(containerErrors)
                .build();
    }

    private List<ContainerLog> aggregateContainerErrors(List<LogEntry> dockerLogs) {
        Map<String, ContainerLog> aggregated = new LinkedHashMap<>();
        for (LogEntry entry : dockerLogs) {
            String line = entry.line != null ? entry.line.trim() : "";
            if (line.isEmpty()) continue;
            String container = entry.labels != null ? entry.labels.getOrDefault("container", "unknown") : "unknown";
            String key = container + "|" + line;
            ContainerLog logItem = aggregated.computeIfAbsent(key, k -> ContainerLog.builder()
                    .container(container)
                    .message(line)
                    .count(0)
                    .firstSeen(entry.timestamp)
                    .lastSeen(entry.timestamp)
                    .build());
            logItem.setCount(logItem.getCount() + 1);
            logItem.setLastSeen(entry.timestamp);
        }
        return aggregated.values().stream()
                .sorted(Comparator.comparing(ContainerLog::getLastSeen).reversed())
                .limit(50)
                .toList();
    }

    private List<LogEntry> queryLoki(String query, Instant start, Instant end, int limit) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = UriComponentsBuilder.fromHttpUrl(lokiBaseUrl + "/loki/api/v1/query_range")
                    .queryParam("query", encodedQuery)
                    .queryParam("limit", Math.max(1, Math.min(limit, 1000)))
                    .queryParam("direction", "backward")
                    .queryParam("start", start.toEpochMilli() * 1_000_000)
                    .queryParam("end", end.toEpochMilli() * 1_000_000)
                    .build(true)
                    .toUri();

            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            if (response == null || !"success".equals(response.get("status"))) {
                log.warn("查询Loki失败: {}", response);
                return Collections.emptyList();
            }
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> results = (List<Map<String, Object>>) data.getOrDefault("result", List.of());
            List<LogEntry> entries = new ArrayList<>();

            for (Map<String, Object> result : results) {
                Map<String, String> stream = (Map<String, String>) result.get("stream");
                List<List<String>> values = (List<List<String>>) result.getOrDefault("values", List.of());
                for (List<String> value : values) {
                    if (value.size() < 2) {
                        continue;
                    }
                    String tsStr = value.get(0);
                    String line = value.get(1);
                    Instant timestamp = Instant.ofEpochSecond(0, Long.parseLong(tsStr));
                    entries.add(new LogEntry(timestamp.toString(), line, stream));
                }
            }
            return entries;
        } catch (Exception e) {
            log.error("查询Loki异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Data
    @AllArgsConstructor
    public static class LogEntry {
        private String timestamp;
        private String line;
        private Map<String, String> labels;
    }

    @Data
    @Builder
    public static class LogSummary {
        private List<LogEntry> appLogs;
        private List<ContainerLog> containerErrors;
    }

    @Data
    @Builder
    public static class ContainerLog {
        private String message;
        private String container;
        private int count;
        private String firstSeen;
        private String lastSeen;
    }
}
