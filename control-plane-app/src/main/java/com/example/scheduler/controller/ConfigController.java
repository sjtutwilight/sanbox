package com.example.scheduler.controller;

import com.example.scheduler.config.DataGeneratorProperties;
import com.example.scheduler.datagenerator.model.DataSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 配置接口：返回前端需要的枚举和默认值
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final DataGeneratorProperties properties;

    /**
     * 获取所有配置信息
     */
    @GetMapping
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        
        // 数据源
        config.put("dataSources", Arrays.stream(DataSourceType.values())
                .map(Enum::name)
                .toList());
        
        // 业务域
        config.put("domains", buildDomainsConfig());
        
        // 生成模式（按业务域分组）
        config.put("patterns", buildPatternsConfig());
        
        // 默认值
        config.put("defaults", buildDefaultsConfig());
        
        return config;
    }

    private List<Map<String, String>> buildDomainsConfig() {
        return List.of(
                Map.of("value", "USER_POSITION", "label", "用户持仓", "description", "用户持仓数据，支持 Hash 存储"),
                Map.of("value", "TRADE_FEED", "label", "成交明细", "description", "交易成交流水，支持 List 存储"),
                Map.of("value", "ACTIVE_USERS", "label", "活跃用户", "description", "每日活跃用户，支持 Set/Bitmap"),
                Map.of("value", "FAVORITE", "label", "自选收藏", "description", "用户自选列表，支持 Set+ZSet")
        );
    }

    private Map<String, List<Map<String, String>>> buildPatternsConfig() {
        Map<String, List<Map<String, String>>> patterns = new LinkedHashMap<>();
        
        patterns.put("USER_POSITION", List.of(
                Map.of("value", "USER_POSITION_PER_USER_HASH", 
                       "label", "正常：每用户独立 Hash",
                       "hint", "pos:user:{id}，按需 HGET/HGETALL 单用户",
                       "risk", "安全：分散 Key，低风险"),
                Map.of("value", "USER_POSITION_GIANT_HASH",
                       "label", "误用：巨型 Hash",
                       "hint", "所有用户塞一个大 Hash，HGETALL 巨 Key 风险",
                       "risk", "高风险：HGETALL 巨 Key")
        ));
        
        patterns.put("TRADE_FEED", List.of(
                Map.of("value", "TRADE_RECENT_LIST",
                       "label", "正常：有界 List + LTRIM",
                       "hint", "List 保留最近 N 条，LRANGE O(k)",
                       "risk", "安全：LTRIM 控制窗口"),
                Map.of("value", "TRADE_HISTORY_LIST",
                       "label", "误用：无界 List",
                       "hint", "全历史 List，LRANGE 越来越慢",
                       "risk", "高风险：长 List O(N) LRANGE")
        ));
        
        patterns.put("ACTIVE_USERS", List.of(
                Map.of("value", "ACTIVE_USERS_DAILY_SET",
                       "label", "按天活跃用户 Set",
                       "hint", "适合 SISMEMBER/SCARD，小规模 SMEMBERS",
                       "risk", "注意：SMEMBERS 仅小规模使用"),
                Map.of("value", "ACTIVE_USERS_BITMAP",
                       "label", "按天活跃用户 Bitmap",
                       "hint", "bitmap 标记活跃用户，节省内存",
                       "risk", "适合大基数活跃，注意 BITCOUNT 开销")
        ));
        
        patterns.put("FAVORITE", List.of(
                Map.of("value", "FAV_NORMAL_SET_ZSET",
                       "label", "正常：每用户 Set+ZSet",
                       "hint", "每用户 set 判重 + zset 按时间排序",
                       "risk", "分散到每用户，风险低"),
                Map.of("value", "FAV_GIANT_SET_ZSET",
                       "label", "误用：全局大 Set+ZSet",
                       "hint", "所有自选塞一个巨型 set/zset",
                       "risk", "巨 Key + 大量 ZRANGE 会阻塞")
        ));
        
        return patterns;
    }

    private Map<String, Object> buildDefaultsConfig() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("valueSizeBytes", properties.getDefaultValueSizeBytes());
        defaults.put("batchSize", properties.getDefaultBatchSize());
        defaults.put("ttlSeconds", properties.getDefaultTtlSeconds());
        defaults.put("keyPrefix", properties.getDefaultKeyPrefix());
        defaults.put("listWindow", properties.getDefaultListWindow());
        defaults.put("favPerUser", properties.getDefaultFavPerUser());
        return defaults;
    }
}

