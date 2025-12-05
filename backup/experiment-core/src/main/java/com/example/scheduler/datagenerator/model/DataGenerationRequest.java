package com.example.scheduler.datagenerator.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 前端发起的数据生成请求
 */
@Data
public class DataGenerationRequest {

    @NotNull
    private DataSourceType dataSource;

    @NotNull
    private DataDomain domain;

    @NotNull
    private GenerationPattern pattern;

    /**
     * 目标记录条数（优先使用），若同时提供 targetSizeMb，则取两者更大。
     */
    @Min(1)
    private Long recordCount;

    /**
     * 目标总大小（MB，近似估算），可选。
     */
    @Min(1)
    private Long targetSizeMb;

    /**
     * 单条value大小（字节级近似），默认配置决定。
     */
    @Min(1)
    private Integer valueSizeBytes;

    /**
     * 写入Redis的key前缀；不同模式可能追加后缀。
     */
    private String keyPrefix;

    /**
     * 可选TTL（秒）；null 表示不设置。
     */
    @Min(1)
    private Integer ttlSeconds;

    /**
     * 批处理大小
     */
    @Min(1)
    private Integer batchSize;

    /**
     * 是否覆盖已有key；仅对 per-user key 生效。
     */
    private Boolean overwrite = Boolean.TRUE;

    /**
     * list 正常模式的保留窗口，未填则走配置默认值。
     */
    @Min(1)
    private Integer listWindow;

    /**
     * 可选：活跃用户集合指定日期（yyyy-MM-dd），未填则用今日。
     */
    private String day;

    /**
     * 自选收藏：每用户收藏数
     */
    @Min(1)
    private Integer favPerUser;

    /**
     * 持续写压测的目标QPS（单线程循环写），0或空表示不限速
     */
    @Min(0)
    private Integer qps;

    /**
     * 是否使用 write-through（同时写入持久层与缓存）
     */
    private Boolean writeThrough = Boolean.FALSE;

    /**
     * 写入/读压时的用户基数（用于随机 userId）
     */
    private Long userCount;

    /**
     * 多周期K线：参与写入的交易对数量
     */
    @Min(1)
    private Integer symbolCount;

    /**
     * 多周期K线：每个交易对的K线长度
     */
    @Min(1)
    private Integer candlesPerSymbol;

    /**
     * 多周期K线：默认写入的周期列表（例：1m、5m、1h、1d）
     */
    private List<String> klineIntervals;

    /**
     * 多周期K线：是否同步写入ZSet便于范围检索
     */
    private Boolean includeZset = Boolean.TRUE;
}
