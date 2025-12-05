package com.example.scheduler.experiment;

import com.example.scheduler.controlplane.client.LoadExecutorClient;
import com.example.scheduler.controlplane.client.dto.ExperimentDescriptorResponse;
import com.example.scheduler.controlplane.client.dto.ExperimentOperationResponse;
import com.example.scheduler.controlplane.client.dto.LoadShapeTemplateResponse;
import com.example.scheduler.controlplane.client.dto.OperationParameterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验服务：从 Load Executor 同步实验元数据。
 */
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final LoadExecutorClient loadExecutorClient;

    public List<Experiment> list() {
        List<ExperimentDescriptorResponse> descriptors = loadExecutorClient.listExperiments();
        return convert(descriptors);
    }

    public Experiment get(String id) {
        return list().stream()
                .filter(exp -> exp.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("实验不存在: " + id));
    }

    public Experiment.ExperimentGroup getGroup(String experimentId, String groupId) {
        Experiment experiment = get(experimentId);
        return experiment.getGroups().stream()
                .filter(group -> group.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("实验组不存在: " + groupId));
    }

    public Experiment.ExperimentOperation getOperation(String experimentId, String groupId, String operationId) {
        Experiment.ExperimentGroup group = getGroup(experimentId, groupId);
        return group.getOperations().stream()
                .filter(op -> op.getId().equals(operationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("操作不存在: " + operationId));
    }

    private List<Experiment> convert(List<ExperimentDescriptorResponse> descriptors) {
        if (CollectionUtils.isEmpty(descriptors)) {
            return List.of();
        }
        Map<String, Experiment.ExperimentBuilder> experimentBuilders = new LinkedHashMap<>();
        Map<String, List<Experiment.ExperimentGroup>> grouped = new LinkedHashMap<>();

        for (ExperimentDescriptorResponse descriptor : descriptors) {
            String experimentId = descriptor.getExperimentId();
            Experiment.ExperimentBuilder expBuilder = experimentBuilders.computeIfAbsent(
                    experimentId,
                    id -> Experiment.builder()
                            .id(id)
                            .name(defaultString(descriptor.getExperimentName(), id))
                            .description(descriptor.getExperimentDescription())
            );
            Experiment.ExperimentGroup group = Experiment.ExperimentGroup.builder()
                    .id(defaultString(descriptor.getGroupId(), "default"))
                    .name(defaultString(descriptor.getGroupName(), descriptor.getGroupId()))
                    .description(descriptor.getGroupDescription())
                    .operations(buildOperations(descriptor.getOperations()))
                    .build();
            grouped.computeIfAbsent(experimentId, key -> new ArrayList<>()).add(group);
        }

        List<Experiment> experiments = new ArrayList<>();
        for (Map.Entry<String, Experiment.ExperimentBuilder> entry : experimentBuilders.entrySet()) {
            List<Experiment.ExperimentGroup> groups = grouped.getOrDefault(entry.getKey(), List.of());
            experiments.add(entry.getValue().groups(groups).build());
        }
        return experiments;
    }

    private List<Experiment.ExperimentOperation> buildOperations(List<ExperimentOperationResponse> operations) {
        if (operations == null || operations.isEmpty()) {
            return List.of();
        }
        List<Experiment.ExperimentOperation> list = new ArrayList<>();
        for (ExperimentOperationResponse op : operations) {
            list.add(Experiment.ExperimentOperation.builder()
                    .id(op.getOperationId())
                    .type(op.getOperationType())
                    .label(defaultString(op.getLabel(), op.getOperationId()))
                    .hint(op.getHint() != null ? op.getHint() : op.getDescription())
                    .parameters(buildParameters(op.getParameters()))
                    .loadShape(buildLoadShape(op.getLoadShapeTemplate()))
                    .build());
        }
        return list;
    }

    private List<OperationParameterDefinition> buildParameters(List<OperationParameterResponse> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        List<OperationParameterDefinition> list = new ArrayList<>();
        for (OperationParameterResponse param : parameters) {
            list.add(OperationParameterDefinition.builder()
                    .name(param.getName())
                    .label(defaultString(param.getLabel(), param.getName()))
                    .type(defaultString(param.getType(), "string"))
                    .required(param.isRequired())
                    .description(param.getDescription())
                    .example(param.getExample())
                    .defaultValue(param.getDefaultValue())
                    .build());
        }
        return list;
    }

    private LoadShapeTemplate buildLoadShape(LoadShapeTemplateResponse template) {
        if (template == null) {
            return null;
        }
        return LoadShapeTemplate.builder()
                .type(template.getType())
                .qps(template.getQps())
                .concurrency(template.getConcurrency())
                .durationSeconds(template.getDurationSeconds())
                .params(template.getParams())
                .build();
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
