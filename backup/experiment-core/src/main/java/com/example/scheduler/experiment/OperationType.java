package com.example.scheduler.experiment;

/**
 * 实验操作类型
 */
public enum OperationType {
    /**
     * 一次性初始化数据
     */
    INIT_DATA,
    
    /**
     * 持续写入负载（需手动停止）
     */
    CONTINUOUS_WRITE,
    
    /**
     * 持续读取负载（需手动停止）
     */
    CONTINUOUS_READ,
    
    /**
     * 初始化MySQL数据
     */
    INIT_MYSQL,
    
    /**
     * 初始化Redis数据
     */
    INIT_REDIS
}

