package com.example.scheduler.service;

import com.example.scheduler.datasource.mysql.entity.FavoriteSymbol;
import com.example.scheduler.datasource.mysql.repo.FavoriteSymbolRepository;
import com.example.scheduler.experiment.CacheExperimentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MySQL数据初始化服务
 * 用于缓存实验的底层数据准备
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlDataInitService {
    
    private final FavoriteSymbolRepository repository;
    
    /**
     * 批量初始化MySQL自选数据
     * @param config MySQL初始化配置
     * @return 已写入记录数
     */
    public long initFavorites(CacheExperimentConfig.MysqlInitConfig config) {
        if (config.isTruncateFirst()) {
            log.info("清空MySQL favorite_symbol表");
            truncate();
        }
        
        long totalRecords = config.getUserCount() * config.getFavPerUser();
        long written = 0;
        int batchSize = config.getBatchSize();
        
        log.info("开始初始化MySQL数据: userCount={}, favPerUser={}, totalRecords={}", 
                config.getUserCount(), config.getFavPerUser(), totalRecords);
        
        List<FavoriteSymbol> batch = new ArrayList<>(batchSize);
        
        for (long userId = 1; userId <= config.getUserCount(); userId++) {
            for (int i = 0; i < config.getFavPerUser(); i++) {
                FavoriteSymbol fav = new FavoriteSymbol();
                fav.setUserId(userId);
                fav.setSymbol("SYM" + ThreadLocalRandom.current().nextInt(1, 10000));
                fav.setTags("mysql-init");
                fav.setCreatedAt(Instant.now());
                
                batch.add(fav);
                written++;
                
                if (batch.size() >= batchSize) {
                    // 每个批次独立提交，避免大事务
                    saveBatch(batch);
                    batch.clear();
                    
                    if (written % 10000 == 0) {
                        log.info("MySQL初始化进度: {}/{} ({}%)", 
                                written, totalRecords, String.format("%.1f", written * 100.0 / totalRecords));
                    }
                }
            }
        }
        
        // 保存剩余数据
        if (!batch.isEmpty()) {
            saveBatch(batch);
        }
        
        log.info("MySQL数据初始化完成: 共写入{}条记录", written);
        return written;
    }
    
    /**
     * 批量保存（独立事务）
     */
    @Transactional
    public void saveBatch(List<FavoriteSymbol> batch) {
        repository.saveAll(batch);
    }
    
    /**
     * 获取当前MySQL中的记录总数
     */
    public long count() {
        return repository.count();
    }
    
    /**
     * 清空所有数据
     */
    @Transactional
    public void truncate() {
        repository.deleteAll();
        log.info("已清空MySQL favorite_symbol表");
    }
}

