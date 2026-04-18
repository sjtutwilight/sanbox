package com.example.scheduler.controller.dto;

import com.example.scheduler.controlplane.client.dto.LoadShapeRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class OperationExecutionRequest {

    /**
     * 显式指定运行平台 profile，启动接口不再允许隐式推断。
     */
    @NotBlank(message = "platform 不能为空")
    private String platform;

    /**
     * 显式指定运行场景 profile，启动接口不再允许隐式推断。
     */
    @NotBlank(message = "scenario 不能为空")
    private String scenario;

    private Map<String, Object> parameters;
    private LoadShapeRequest loadShape;
}
