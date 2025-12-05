package com.example.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 定时任务配置类
 * 从application.yml读取配置参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerConfig {
    
    /**
     * 定时任务执行间隔(毫秒)
     */
    private Long fixedRate = 5000L;
    
    /**
     * 数据写入配置
     */
    private DataConfig data = new DataConfig();
    
    @Data
    public static class DataConfig {
        /**
         * 每次写入的数据条数
         */
        private Integer batchSize = 10;
        
        /**
         * Redis Key前缀
         */
        private String keyPrefix = "scheduler:data:";
    }
}
