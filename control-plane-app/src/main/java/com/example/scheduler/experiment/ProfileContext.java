package com.example.scheduler.experiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运行 profile 上下文。
 * <p>
 * 这里把 platform / scenario 作为最小可追踪单元，供控制面启动、状态回填和日志定位共用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileContext {

    /**
     * 平台 profile，例如 k3s / docker。
     */
    private String platform;

    /**
     * 场景 profile，例如 favorite-read-cache-aside / redis-sharding。
     */
    private String scenario;
}
