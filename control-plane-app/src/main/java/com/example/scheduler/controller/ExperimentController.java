package com.example.scheduler.controller;

import com.example.scheduler.controlplane.ExperimentOperationCoordinator;
import com.example.scheduler.controller.dto.OperationExecutionRequest;
import com.example.scheduler.experiment.Experiment;
import com.example.scheduler.experiment.ExperimentService;
import com.example.scheduler.experiment.LoadTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 实验管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;
    private final ExperimentOperationCoordinator operationCoordinator;

    /**
     * 获取所有实验列表
     */
    @GetMapping
    public List<Experiment> list() {
        return experimentService.list();
    }

    /**
     * 获取单个实验详情
     */
    @GetMapping("/{id}")
    public Experiment get(@PathVariable String id) {
        try {
            return experimentService.get(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 启动操作
     */
    @PostMapping("/{expId}/groups/{groupId}/operations/{opId}/start")
    public LoadTask startOperation(
            @PathVariable String expId,
            @PathVariable String groupId,
            @PathVariable String opId,
            @RequestHeader(value = "X-Experiment-Id", required = false) String experimentRunId,
            @Valid @RequestBody OperationExecutionRequest request) {
        try {
            return operationCoordinator.startOperation(
                    expId,
                    groupId,
                    opId,
                    experimentRunId,
                    request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }


    /**
     * 停止操作
     */
    @PostMapping("/{expId}/groups/{groupId}/operations/{opId}/stop")
    public LoadTask stopOperation(
            @PathVariable String expId,
            @PathVariable String groupId,
            @PathVariable String opId) {
        return operationCoordinator.stopOperation(expId, groupId, opId);
    }

    /**
     * 获取操作状态
     */
    @GetMapping("/{expId}/groups/{groupId}/operations/{opId}/status")
    public LoadTask getOperationStatus(
            @PathVariable String expId,
            @PathVariable String groupId,
            @PathVariable String opId) {
        return operationCoordinator.getStatus(expId, groupId, opId);
    }

    /**
     * 获取所有运行中的任务
     */
    @GetMapping("/running-tasks")
    public List<LoadTask> getRunningTasks() {
        return operationCoordinator.getRunningTasks();
    }

    /**
     * 批量获取操作状态（用于前端轮询）
     */
    @PostMapping("/batch-status")
    public Map<String, LoadTask> batchGetStatus(@RequestBody List<Map<String, String>> requests) {
        return requests.stream()
                .collect(java.util.stream.Collectors.toMap(
                        req -> req.get("expId") + ":" + req.get("groupId") + ":" + req.get("opId"),
                        req -> {
                            return operationCoordinator.getStatus(
                                    req.get("expId"), req.get("groupId"), req.get("opId"));
                        },
                        (a, b) -> a
                ));
    }
}
