package com.example.scheduler.service;

import com.example.scheduler.config.SchedulerConfig;
import com.example.scheduler.model.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis数据服务
 * 负责向Redis写入数据
 */
@Slf4j
@Service
public class RedisDataService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final SchedulerConfig schedulerConfig;
    
    public RedisDataService(RedisTemplate<String, Object> redisTemplate, 
                           SchedulerConfig schedulerConfig) {
        this.redisTemplate = redisTemplate;
        this.schedulerConfig = schedulerConfig;
    }

    /**
     * 批量写入数据到Redis
     * 
     * @return 包含写入结果的数组[成功数, 总数, 数据值列表]
     */
    public Object[] batchWriteData() {
        int batchSize = schedulerConfig.getData().getBatchSize();
        String keyPrefix = schedulerConfig.getData().getKeyPrefix();
        
        List<DataRecord> records = generateDataRecords(batchSize);
        List<Double> values = new ArrayList<>();
        
        int successCount = 0;
        for (DataRecord record : records) {
            try {
                String key = keyPrefix + record.getId();
                // 写入Redis,设置过期时间为1小时
                redisTemplate.opsForValue().set(key, record, 1, TimeUnit.HOURS);
                successCount++;
                values.add(record.getValue());
                log.debug("成功写入数据到Redis: key={}, data={}", key, record);
            } catch (Exception e) {
                log.error("写入数据到Redis失败: {}", record, e);
            }
        }
        
        log.info("批量写入数据完成: 总数={}, 成功={}", batchSize, successCount);
        return new Object[]{successCount, batchSize, values};
    }
    
    /**
     * 生成测试数据
     */
    private List<DataRecord> generateDataRecords(int count) {
        List<DataRecord> records = new ArrayList<>();
        String[] types = {"TEMPERATURE", "HUMIDITY", "PRESSURE", "SPEED", "VOLTAGE"};
        
        for (int i = 0; i < count; i++) {
            DataRecord record = DataRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .content("数据记录-" + System.currentTimeMillis() + "-" + i)
                    .value(Math.random() * 100)
                    .type(types[(int) (Math.random() * types.length)])
                    .createTime(LocalDateTime.now())
                    .build();
            records.add(record);
        }
        
        return records;
    }
    
    /**
     * 获取Redis中的数据总数
     */
    public long getDataCount() {
        String pattern = schedulerConfig.getData().getKeyPrefix() + "*";
        Long count = redisTemplate.countExistingKeys(
            redisTemplate.keys(pattern)
        );
        return count != null ? count : 0L;
    }
}
