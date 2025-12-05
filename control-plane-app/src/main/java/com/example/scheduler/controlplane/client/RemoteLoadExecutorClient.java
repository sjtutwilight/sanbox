package com.example.scheduler.controlplane.client;

import com.example.scheduler.config.LoadExecutorProperties;
import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.controlplane.client.dto.CommandRequest;
import com.example.scheduler.controlplane.client.dto.ExperimentDescriptorResponse;
import com.example.scheduler.controlplane.client.dto.ExperimentRunResponse;
import com.example.scheduler.controlplane.client.dto.LoadShapeRequest;
import com.example.scheduler.controlplane.client.dto.RunMetricsResponse;
import com.example.scheduler.controlplane.client.dto.RunStatus;
import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.experiment.LoadTaskStatus;
import com.example.scheduler.experiment.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 HTTP 的 Load Executor Client，实现控制面与负载执行器的解耦。
 */
@Slf4j
@Component
public class RemoteLoadExecutorClient implements LoadExecutorClient {

    private final RestTemplate restTemplate;
    private final LoadExecutorProperties properties;
    private static final String LOAD_SHAPE_KEY = "__loadShape";

    public RemoteLoadExecutorClient(LoadExecutorProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    @Override
    public LoadTask submit(ExecutorCommand command) {
        CommandRequest request = toCommandRequest(command);
        ExperimentRunResponse response = post(new String[]{"commands"}, request, ExperimentRunResponse.class);
        return toLoadTask(response);
    }

    @Override
    public LoadTask stop(String experimentRunId) {
        ExperimentRunResponse response = post(new String[]{"commands", experimentRunId, "stop"}, null, ExperimentRunResponse.class);
        return toLoadTask(response);
    }

    @Override
    public LoadTask getRun(String experimentRunId) {
        ExperimentRunResponse response = get(new String[]{"runs", experimentRunId}, ExperimentRunResponse.class);
        return toLoadTask(response);
    }

    @Override
    public List<LoadTask> listRuns() {
        try {
            RequestEntity<Void> request = new RequestEntity<>(HttpMethod.GET, buildUri("runs"));
            ResponseEntity<List<ExperimentRunResponse>> response = restTemplate.exchange(
                    request,
                    new ParameterizedTypeReference<List<ExperimentRunResponse>>() {});
            List<ExperimentRunResponse> runs = response.getBody();
            if (runs == null || runs.isEmpty()) {
                return Collections.emptyList();
            }
            return runs.stream()
                    .map(this::toLoadTask)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RestClientException e) {
            log.error("调用 Load Executor 获取运行列表失败", e);
            throw new IllegalStateException("调用 Load Executor 获取运行列表失败", e);
        }
    }

    @Override
    public List<ExperimentDescriptorResponse> listExperiments() {
        try {
            RequestEntity<Void> request = new RequestEntity<>(HttpMethod.GET, buildUri("experiments"));
            ResponseEntity<List<ExperimentDescriptorResponse>> response = restTemplate.exchange(
                    request,
                    new ParameterizedTypeReference<List<ExperimentDescriptorResponse>>() {});
            List<ExperimentDescriptorResponse> descriptors = response.getBody();
            return descriptors != null ? descriptors : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("调用 Load Executor 获取实验元数据失败", e);
            throw new IllegalStateException("调用 Load Executor 获取实验元数据失败", e);
        }
    }

    private CommandRequest toCommandRequest(ExecutorCommand command) {
        Map<String, Object> payload = copyOverrides(command.getOverrides());
        LoadShapeRequest loadShape = resolveLoadShape(payload);
        return CommandRequest.builder()
                .commandId(command.getTaskId())
                .experimentId(command.getExperimentId())
                .groupId(command.getGroupId())
                .operationId(command.getOperationId())
                .experimentRunId(command.getExperimentRunId())
                .operationType(command.getOperationType())
                .dataRequest(payload)
                .overrides(payload)
                .loadShape(loadShape)
                .build();
    }

    private LoadShapeRequest buildLoadShape(Map<String, Object> overrides) {
        int qps = resolveInt(overrides, "qps", properties.getDefaultQps());
        int concurrency = resolveInt(overrides, "concurrency", properties.getDefaultConcurrency());
        Long duration = resolveLong(overrides, "durationSeconds", properties.getDefaultDurationSeconds());
        String shapeType = StringUtils.hasText(properties.getDefaultShapeType())
                ? properties.getDefaultShapeType()
                : "CONSTANT";
        return LoadShapeRequest.builder()
                .type(shapeType)
                .qps(Math.max(1, qps))
                .concurrency(Math.max(1, concurrency))
                .durationSeconds(duration != null && duration > 0 ? duration : null)
                .build();
    }

    private Map<String, Object> copyOverrides(Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(overrides);
    }

    @SuppressWarnings("unchecked")
    private LoadShapeRequest resolveLoadShape(Map<String, Object> overrides) {
        Object explicitShape = overrides.remove(LOAD_SHAPE_KEY);
        if (explicitShape instanceof Map<?, ?> map) {
            return loadShapeFromMap((Map<String, Object>) map);
        }
        return buildLoadShape(overrides);
    }

    private LoadShapeRequest loadShapeFromMap(Map<String, Object> map) {
        if (map == null) {
            return buildLoadShape(null);
        }
        String type = map.get("type") != null ? map.get("type").toString() : properties.getDefaultShapeType();
        Integer qps = map.get("qps") instanceof Number n ? n.intValue() : properties.getDefaultQps();
        Integer concurrency = map.get("concurrency") instanceof Number n ? n.intValue() : properties.getDefaultConcurrency();
        Long duration = map.get("durationSeconds") instanceof Number n ? n.longValue() : properties.getDefaultDurationSeconds();
        Map<String, Object> params = map.get("params") instanceof Map<?, ?> paramMap
                ? new LinkedHashMap<>((Map<String, Object>) paramMap)
                : null;
        return LoadShapeRequest.builder()
                .type(type)
                .qps(qps != null ? qps : properties.getDefaultQps())
                .concurrency(concurrency)
                .durationSeconds(duration)
                .params(params)
                .build();
    }

    private int resolveInt(Map<String, Object> source, String key, int defaultValue) {
        Object value = source != null ? source.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private Long resolveLong(Map<String, Object> source, String key, Long defaultValue) {
        Object value = source != null ? source.get(key) : null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private LoadTask toLoadTask(ExperimentRunResponse response) {
        if (response == null) {
            return null;
        }
        LoadTask task = LoadTask.builder()
                .id(extractCommandId(response))
                .experimentId(response.getExperimentId())
                .experimentRunId(response.getExperimentRunId())
                .groupId(response.getGroupId())
                .operationId(response.getOperationId())
                .type(extractOperationType(response))
                .status(mapStatus(response.getStatus()))
                .startedAt(response.getStartedAt())
                .endedAt(response.getEndedAt())
                .lastError(response.getLastError())
                .build();
        RunMetricsResponse metrics = response.getMetrics();
        if (metrics != null) {
            task.setCurrentOpsPerSec(metrics.getCurrentQps());
            task.setAvgLatencyMs(metrics.getAvgLatencyMs());
            task.setMaxLatencyMs(metrics.getMaxLatencyMs());
            task.getTotalOps().set(metrics.getTotalRequests());
            task.getErrors().set(metrics.getFailedRequests());
        }
        return task;
    }

    private String extractCommandId(ExperimentRunResponse response) {
        Map<String, Object> command = response.getCommand();
        if (command != null) {
            Object id = command.get("commandId");
            if (id != null) {
                return id.toString();
            }
        }
        if (response.getExperimentId() == null) {
            return null;
        }
        return String.join(":", Optional.ofNullable(response.getExperimentId()).orElse(""),
                Optional.ofNullable(response.getGroupId()).orElse(""),
                Optional.ofNullable(response.getOperationId()).orElse(""));
    }

    private OperationType extractOperationType(ExperimentRunResponse response) {
        Map<String, Object> command = response.getCommand();
        if (command == null) {
            return null;
        }
        Object type = command.get("operationType");
        if (type == null) {
            return null;
        }
        try {
            return OperationType.valueOf(type.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LoadTaskStatus mapStatus(RunStatus status) {
        if (status == null) {
            return LoadTaskStatus.IDLE;
        }
        return switch (status) {
            case INIT -> LoadTaskStatus.IDLE;
            case RUNNING, PAUSED -> LoadTaskStatus.RUNNING;
            case STOPPED -> LoadTaskStatus.STOPPED;
            case COMPLETED -> LoadTaskStatus.COMPLETED;
            case FAILED -> LoadTaskStatus.FAILED;
        };
    }

    private <T> T post(String[] segments, Object payload, Class<T> type) {
        try {
            return restTemplate.postForObject(buildUri(segments), payload, type);
        } catch (RestClientException e) {
            log.error("调用 Load Executor 接口({})失败: {}", String.join("/", segments), e.getMessage());
            throw new IllegalStateException("调用 Load Executor 失败: " + String.join("/", segments), e);
        }
    }

    private <T> T get(String[] segments, Class<T> type) {
        try {
            return restTemplate.getForObject(buildUri(segments), type);
        } catch (RestClientException e) {
            log.error("调用 Load Executor 接口({})失败: {}", String.join("/", segments), e.getMessage());
            throw new IllegalStateException("调用 Load Executor 失败: " + String.join("/", segments), e);
        }
    }

    private URI buildUri(String... segments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl());
        if (segments != null) {
            for (String segment : segments) {
                if (segment == null) {
                    continue;
                }
                builder.pathSegment(segment);
            }
        }
        return builder.build().toUri();
    }
}
