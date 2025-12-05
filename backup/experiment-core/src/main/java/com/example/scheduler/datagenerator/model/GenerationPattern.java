package com.example.scheduler.datagenerator.model;

/**
 * 生成模式：覆盖 redis实验.md 的正常/误用场景
 */
public enum GenerationPattern {
    /**
     * 1A 正常：每用户一个hash
     */
    USER_POSITION_PER_USER_HASH,
    /**
     * 1A 误用：全部用户塞进一个巨型hash
     */
    USER_POSITION_GIANT_HASH,

    /**
     * 1B 正常：近期成交列表并裁剪窗口
     */
    TRADE_RECENT_LIST,
    /**
     * 1B 误用：全量成交长list，不裁剪
     */
    TRADE_HISTORY_LIST,

    /**
     * 1C 按天活跃用户集合
     */
    ACTIVE_USERS_DAILY_SET
    ,
    /**
     * 按日活跃用户 bitmap，用于与 set 场景对照
     */
    ACTIVE_USERS_BITMAP,

    /**
     * 自选收藏：按用户维护 set + zset
     */
    FAV_NORMAL_SET_ZSET,
    /**
     * 自选收藏：误用，所有用户放一个巨型 set/zset
     */
    FAV_GIANT_SET_ZSET,

    /**
     * 多周期K线：按时间窗口裁剪的列表+ZSet
     */
    KLINE_MULTI_WINDOW_BOUNDED,
    /**
     * 多周期K线：误用，无界 list/zset
     */
    KLINE_MULTI_WINDOW_GIANT
}
