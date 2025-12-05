package com.example.scheduler.loadexecutor.experiment.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.example.scheduler.loadexecutor.domain.Command;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "experiment.dynamic-config", name = "enabled", havingValue = "true")
public class NacosExperimentParameterOverrideService implements ExperimentParameterOverrideService, ExperimentDynamicConfigPublisher {

    private final ExperimentDynamicConfigProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicReference<Map<String, Map<String, Object>>> overrides = new AtomicReference<>(Collections.emptyMap());
    private volatile ConfigService configService;
    private volatile Listener listener;

    @PostConstruct
    public void init() {
        try {
            this.configService = NacosFactory.createConfigService(buildProperties());
            String initial = configService.getConfig(properties.getDataId(), resolvedGroup(), properties.getTimeoutMs());
            refresh(initial);
            this.listener = new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    refresh(configInfo);
                }
            };
            configService.addListener(properties.getDataId(), resolvedGroup(), listener);
            log.info("Nacos experiment overrides listening on dataId={} group={}", properties.getDataId(), resolvedGroup());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Nacos experiment config", e);
        }
    }

    @Override
    public Map<String, Object> currentParameters(Command command) {
        if (command == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        String experiment = normalize(command.getExperimentId());
        String group = normalize(command.getGroupId());
        String operation = normalize(command.getOperationId());
        mergeForKey(merged, key("*", "*", "*"));
        mergeForKey(merged, key(experiment, "*", "*"));
        mergeForKey(merged, key(experiment, group, "*"));
        mergeForKey(merged, key(experiment, group, operation));
        return merged.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(merged);
    }

    @Override
    public synchronized void publish(String experimentId, String groupId, String operationId, Map<String, Object> overridesPayload) {
        if (configService == null) {
            log.warn("Nacos configService not initialized, cannot publish overrides");
            return;
        }
        String key = key(normalize(experimentId), normalize(groupId), normalize(operationId));
        Map<String, Map<String, Object>> current = new LinkedHashMap<>(this.overrides.get());
        if (overridesPayload == null || overridesPayload.isEmpty()) {
            current.remove(key);
        } else {
            current.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(overridesPayload)));
        }
        try {
            String json = objectMapper.writeValueAsString(current);
            boolean success = configService.publishConfig(properties.getDataId(), resolvedGroup(), json);
            if (success) {
                refresh(json);
                log.info("Published dynamic config for {}", key);
            } else {
                log.warn("Failed to publish dynamic config for {}", key);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish dynamic experiment overrides", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (configService != null && listener != null) {
            configService.removeListener(properties.getDataId(), resolvedGroup(), listener);
        }
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (Exception e) {
                log.warn("Failed to shut down Nacos config service", e);
            }
        }
    }

    private void mergeForKey(Map<String, Object> target, String key) {
        Map<String, Object> source = overrides.get().get(key);
        if (source != null) {
            target.putAll(source);
        }
    }

    private void refresh(String raw) {
        if (!StringUtils.hasText(raw)) {
            overrides.set(Collections.emptyMap());
            log.info("Experiment parameter overrides cleared due to empty Nacos config");
            return;
        }
        try {
            Map<String, Map<String, Object>> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            Map<String, Map<String, Object>> normalized = new LinkedHashMap<>();
            parsed.forEach((key, value) -> {
                if (!StringUtils.hasText(key) || value == null) {
                    return;
                }
                normalized.put(normalizeKey(key), Collections.unmodifiableMap(new LinkedHashMap<>(value)));
            });
            overrides.set(Collections.unmodifiableMap(normalized));
            log.info("Loaded {} experiment override entries from Nacos", normalized.size());
        } catch (Exception e) {
            log.warn("Failed to parse experiment overrides from Nacos", e);
        }
    }

    private Properties buildProperties() {
        Properties props = new Properties();
        props.put(PropertyKeyConst.SERVER_ADDR, Objects.requireNonNull(properties.getServerAddr(), "nacos serverAddr is required"));
        if (StringUtils.hasText(properties.getNamespace())) {
            props.put(PropertyKeyConst.NAMESPACE, properties.getNamespace());
        }
        if (StringUtils.hasText(properties.getUsername())) {
            props.put(PropertyKeyConst.USERNAME, properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            props.put(PropertyKeyConst.PASSWORD, properties.getPassword());
        }
        return props;
    }

    private String resolvedGroup() {
        return StringUtils.hasText(properties.getGroup()) ? properties.getGroup() : "DEFAULT_GROUP";
    }

    private String key(String experiment, String group, String operation) {
        return String.join("/", normalize(experiment), normalize(group), normalize(operation));
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        String text = value.trim();
        if ("*".equals(text)) {
            return "*";
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private String normalizeKey(String rawKey) {
        String[] parts = rawKey.split("/");
        String experiment = parts.length > 0 ? parts[0] : "default";
        String group = parts.length > 1 ? parts[1] : "*";
        String operation = parts.length > 2 ? parts[2] : "*";
        return key(experiment, group, operation);
    }
}
