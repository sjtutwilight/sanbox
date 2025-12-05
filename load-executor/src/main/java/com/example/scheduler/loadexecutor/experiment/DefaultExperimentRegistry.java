package com.example.scheduler.loadexecutor.experiment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DefaultExperimentRegistry implements ExperimentRegistry {

    private final Map<String, ExperimentOperationHandle> index = new ConcurrentHashMap<>();
    private final List<ExperimentDescriptor> descriptors;

    public DefaultExperimentRegistry(List<ExperimentGroup> groups) {
        List<ExperimentGroup> safeList = groups != null ? groups : Collections.emptyList();
        this.descriptors = safeList.stream()
                .map(this::toDescriptor)
                .toList();
        for (ExperimentDescriptor descriptor : descriptors) {
            for (ExperimentOperationDefinition definition : descriptor.getOperations()) {
                ExperimentOperationHandle handle = ExperimentOperationHandle.builder()
                        .experimentId(descriptor.getExperimentId())
                        .groupId(descriptor.getGroupId())
                        .operationId(definition.getOperationId())
                        .operationType(definition.getOperationType())
                        .invoker(definition.getInvoker())
                        .descriptor(descriptor)
                        .build();
                String key = key(descriptor.getExperimentId(), descriptor.getGroupId(), definition.getOperationId());
                index.put(key, handle);
            }
        }
        log.info("Registered {} experiment groups, {} operations", safeList.size(), index.size());
    }

    private ExperimentDescriptor toDescriptor(ExperimentGroup group) {
        return ExperimentDescriptor.builder()
                .experimentId(group.experimentId())
                .experimentName(group.experimentName())
                .groupId(group.groupId())
                .groupName(group.groupName())
                .groupDescription(group.groupDescription())
                .description(group.getClass().getSimpleName())
                .operations(group.operations())
                .build();
    }

    private String key(String experimentId, String groupId, String operationId) {
        return String.join(":",
                normalize(experimentId),
                normalize(groupId),
                normalize(operationId));
    }

    private String normalize(String value) {
        return value == null ? "default" : value.toLowerCase();
    }

    @Override
    public Optional<ExperimentOperationHandle> find(String experimentId, String groupId, String operationId) {
        return Optional.ofNullable(index.get(key(experimentId, groupId, operationId)));
    }

    @Override
    public Collection<ExperimentDescriptor> descriptors() {
        return descriptors;
    }
}
