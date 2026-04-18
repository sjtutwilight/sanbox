package com.example.scheduler.experiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 operation 支持的 profile 定义。
 * <p>
 * 该对象只描述“允许什么组合”，不承载运行时状态，便于控制面做校验并向前端回传可选项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationProfileDefinition {

    /**
     * 平台 profile。
     */
    private String platform;

    /**
     * 场景 profile。
     */
    private String scenario;

    /**
     * 前端展示用名称。
     */
    private String label;

    /**
     * 前端展示用说明。
     */
    private String description;

    /**
     * 是否为推荐默认项。
     */
    @Builder.Default
    private boolean recommended = false;
}
