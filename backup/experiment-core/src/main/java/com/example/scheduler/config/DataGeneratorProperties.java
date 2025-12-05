package com.example.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 前端数据生成器配置默认值
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "data-generator")
public class DataGeneratorProperties {

    /**
     * 默认单条value大小（字节级近似，通过padding字符串控制）
     */
    private int defaultValueSizeBytes = 1024;

    /**
     * 默认批次大小
     */
    private int defaultBatchSize = 5000;

    /**
     * 默认TTL（秒）
     */
    private int defaultTtlSeconds = 3600;

    /**
     * 默认key前缀
     */
    private String defaultKeyPrefix = "dg:";

    /**
     * list 正常模式的裁剪窗口
     */
    private int defaultListWindow = 1000;

    /**
     * 自选收藏默认每用户收藏数
     */
    private int defaultFavPerUser = 200;
}
