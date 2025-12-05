package com.example.scheduler.experiment;

/**
 * 负载任务状态
 */
public enum LoadTaskStatus {
    /**
     * 未启动
     */
    IDLE,
    
    /**
     * 运行中
     */
    RUNNING,
    
    /**
     * 已完成（用于一次性任务）
     */
    COMPLETED,
    
    /**
     * 已停止（用于持续任务）
     */
    STOPPED,
    
    /**
     * 执行失败
     */
    FAILED
}

