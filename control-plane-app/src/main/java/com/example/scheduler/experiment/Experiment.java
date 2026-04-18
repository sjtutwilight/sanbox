package com.example.scheduler.experiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实验元数据，供前端展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experiment {

    private String id;
    private String name;
    private String description;
    private String objective;
    private String architecture;
    private List<String> observePoints;
    private List<String> riskWarnings;
    private List<String> metricsToWatch;
    private List<String> recommendations;
    private List<ExperimentGroup> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentGroup {
        private String id;
        private String name;
        private String description;
        private List<ExperimentOperation> operations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentOperation {
        private String id;
        private OperationType type;
        private String label;
        private String hint;
        /**
         * 该 operation 当前支持的 platform/scenario profile 列表。
         */
        private List<OperationProfileDefinition> supportedProfiles;
        private List<OperationParameterDefinition> parameters;
        private LoadShapeTemplate loadShape;
    }
}
