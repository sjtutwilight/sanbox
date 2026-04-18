package com.example.scheduler.experiment;

import com.example.scheduler.controlplane.client.dto.ExperimentOperationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 控制面 profile 目录。
 * <p>
 * 由于本次不改动 executor，这里直接在控制面维护 operation -> profile 的可选组合，避免把运行语义绑死在执行层。
 */
@Slf4j
@Service
public class ExperimentProfileCatalog {

    /**
     * 为 operation 生成可选 profile 列表。
     * <p>
     * 规则优先级：operationId 定制规则 > operationType 通用规则 > 保底规则。
     */
    public List<OperationProfileDefinition> resolveProfiles(ExperimentOperationResponse operation) {
        if (operation == null) {
            return List.of(defaultProfile("unknown", "unknown"));
        }

        List<OperationProfileDefinition> profiles = switch (operation.getOperationId()) {
            case "read_cache_aside" -> favoriteReadProfiles();
            case "add_favorite", "remove_favorite" -> favoriteWriteProfiles();
            case "warm_cache" -> favoriteWarmProfiles();
            default -> resolveByType(operation);
        };

        if (profiles == null || profiles.isEmpty()) {
            return List.of(defaultProfile("unknown", normalizeScenario(operation.getOperationId())));
        }
        return profiles;
    }

    /**
     * 校验请求 profile 是否落在该 operation 的支持列表里。
     */
    public OperationProfileDefinition validateProfile(ExperimentOperationResponse operation, String platform, String scenario) {
        List<OperationProfileDefinition> supportedProfiles = resolveProfiles(operation);
        return supportedProfiles.stream()
                .filter(profile -> sameProfile(profile, platform, scenario))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(buildUnsupportedMessage(supportedProfiles, platform, scenario)));
    }

    private List<OperationProfileDefinition> resolveByType(ExperimentOperationResponse operation) {
        if (operation == null || operation.getOperationType() == null) {
            return List.of(defaultProfile("k3s", "generic"));
        }
        return switch (operation.getOperationType()) {
            case INIT_DATA -> List.of(
                    profile("k3s", "data-seed", "k3s / data-seed", "K3s 平台上的数据初始化 profile", true),
                    profile("docker", "data-seed", "docker / data-seed", "Docker 本地仿真下的数据初始化 profile", false)
            );
            case CONTINUOUS_WRITE -> List.of(
                    profile("k3s", "favorite-write", "k3s / favorite-write", "K3s 平台上的连续写入 profile", true),
                    profile("docker", "favorite-write", "docker / favorite-write", "Docker 本地仿真下的连续写入 profile", false)
            );
            case CONTINUOUS_READ -> List.of(
                    profile("k3s", "favorite-read-cache-aside", "k3s / favorite-read-cache-aside", "K3s 平台上的读缓存回源 profile", true),
                    profile("docker", "favorite-read-cache-aside", "docker / favorite-read-cache-aside", "Docker 本地仿真下的读缓存回源 profile", false)
            );
            case INIT_MYSQL -> List.of(
                    profile("k3s", "mysql-bootstrap", "k3s / mysql-bootstrap", "K3s 平台上的 MySQL 初始化 profile", true),
                    profile("docker", "mysql-bootstrap", "docker / mysql-bootstrap", "Docker 本地仿真下的 MySQL 初始化 profile", false)
            );
            case INIT_REDIS -> List.of(
                    profile("k3s", "redis-bootstrap", "k3s / redis-bootstrap", "K3s 平台上的 Redis 初始化 profile", true),
                    profile("docker", "redis-bootstrap", "docker / redis-bootstrap", "Docker 本地仿真下的 Redis 初始化 profile", false)
            );
        };
    }

    private List<OperationProfileDefinition> favoriteReadProfiles() {
        return List.of(
                profile("k3s", "favorite-read-cache-aside", "k3s / favorite-read-cache-aside", "自选读缓存回源压测 profile", true),
                profile("docker", "favorite-read-cache-aside", "docker / favorite-read-cache-aside", "自选读缓存回源本地仿真 profile", false)
        );
    }

    private List<OperationProfileDefinition> favoriteWriteProfiles() {
        return List.of(
                profile("k3s", "favorite-write", "k3s / favorite-write", "自选写入压测 profile", true),
                profile("docker", "favorite-write", "docker / favorite-write", "自选写入本地仿真 profile", false)
        );
    }

    private List<OperationProfileDefinition> favoriteWarmProfiles() {
        return List.of(
                profile("k3s", "favorite-warm-cache", "k3s / favorite-warm-cache", "自选缓存预热 profile", true),
                profile("docker", "favorite-warm-cache", "docker / favorite-warm-cache", "自选缓存预热本地仿真 profile", false)
        );
    }

    private OperationProfileDefinition defaultProfile(String platform, String scenario) {
        return profile(platform, scenario, platform + " / " + scenario, "保底 profile", true);
    }

    private OperationProfileDefinition profile(String platform, String scenario, String label, String description, boolean recommended) {
        return OperationProfileDefinition.builder()
                .platform(platform)
                .scenario(scenario)
                .label(label)
                .description(description)
                .recommended(recommended)
                .build();
    }

    private boolean sameProfile(OperationProfileDefinition profile, String platform, String scenario) {
        return profile != null
                && equalsIgnoreCase(profile.getPlatform(), platform)
                && equalsIgnoreCase(profile.getScenario(), scenario);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private String buildUnsupportedMessage(List<OperationProfileDefinition> supportedProfiles, String platform, String scenario) {
        List<String> choices = new ArrayList<>();
        for (OperationProfileDefinition profile : supportedProfiles) {
            choices.add(profile.getPlatform() + "/" + profile.getScenario());
        }
        return String.format(Locale.ROOT, "不支持的 profile: %s/%s，当前 operation 允许的 profile: %s",
                platform, scenario, String.join(", ", choices));
    }

    private String normalizeScenario(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
