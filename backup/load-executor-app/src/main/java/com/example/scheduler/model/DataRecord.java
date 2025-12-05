package com.example.scheduler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据记录模型
 * 用于存储到Redis的数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRecord implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 记录ID
     */
    private String id;
    
    /**
     * 数据内容
     */
    private String content;
    
    /**
     * 数据值
     */
    private Double value;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 数据类型
     */
    private String type;
}
