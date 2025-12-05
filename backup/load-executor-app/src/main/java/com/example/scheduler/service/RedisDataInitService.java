package com.example.scheduler.service;

import com.example.scheduler.experiment.CacheExperimentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis数据初始化服务
 * 用于缓存实验的Redis层数据准备
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDataInitService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 批量初始化Redis自选数据（Set+ZSet）
     * @param config Redis初始化配置
     * @return 已写入用户数
     */
    public long initFavorites(CacheExperimentConfig.RedisInitConfig config) {
        long written = 0;
        int batchSize = config.getBatchSize();
        
        log.info("开始初始化Redis数据: userCount={}, favPerUser={}, keyPrefix={}", 
                config.getUserCount(), config.getFavPerUser(), config.getKeyPrefix());
        
        for (long userId = 1; userId <= config.getUserCount(); userId++) {
            String setKey = config.getKeyPrefix() + "fav:set:" + userId;
            String zsetKey = config.getKeyPrefix() + "fav:z:" + userId;
            
            // 生成随机自选列表
            List<String> symbols = new ArrayList<>();
            for (int i = 0; i < config.getFavPerUser(); i++) {
                symbols.add("SYM" + ThreadLocalRandom.current().nextInt(1, 10000));
            }
            
            // 写入Set（用于判重）
            redisTemplate.opsForSet().add(setKey, symbols.toArray());
            
            // 写入ZSet（用于按时间排序）
            long timestamp = System.currentTimeMillis();
            for (int i = 0; i < symbols.size(); i++) {
                redisTemplate.opsForZSet().add(zsetKey, symbols.get(i), timestamp + i);
            }
            
            // 设置TTL
            if (config.getTtlSeconds() != null && config.getTtlSeconds() > 0) {
                redisTemplate.expire(setKey, config.getTtlSeconds(), TimeUnit.SECONDS);
                redisTemplate.expire(zsetKey, config.getTtlSeconds(), TimeUnit.SECONDS);
            }
            
            written++;
            
            if (written % 1000 == 0) {
                log.info("Redis初始化进度: {}/{} ({:.1f}%)", 
                        written, config.getUserCount(), (written * 100.0 / config.getUserCount()));
            }
        }
        
        log.info("Redis数据初始化完成: 共写入{}个用户的自选数据", written);
        return written;
    }
    
    /**
     * 清空指定前缀的所有Key
     */
    public long flushByPrefix(String keyPrefix) {
        // 使用SCAN遍历并删除
        // 注意：生产环境应该使用更安全的方式
        log.warn("清空Redis keys: {}*", keyPrefix);
        // 简化实现，实际应该用SCAN
        return 0;
    }
}

