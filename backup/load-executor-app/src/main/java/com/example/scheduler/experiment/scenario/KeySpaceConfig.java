package com.example.scheduler.experiment.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Key空间配置 - 定义一组key的分布规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeySpaceConfig {
    
    /**
     * 名称标识，如 "hot_users", "cold_users"
     */
    private String name;
    
    /**
     * Key模式，如 "user:${id}", "fav:z:${id}"
     */
    private String pattern;
    
    /**
     * ID范围 [min, max]
     */
    private long[] idRange;
    
    /**
     * 流量占比，如 0.8 表示 80% 的请求打到这个空间
     */
    private double trafficShare;
    
    /**
     * ID分布类型: uniform / zipf
     */
    @Builder.Default
    private IdDistribution idDistribution = IdDistribution.UNIFORM;
    
    /**
     * Zipf分布参数（仅当 idDistribution = ZIPF 时有效）
     */
    private ZipfParams zipfParams;
    
    /**
     * ID分布类型枚举
     */
    public enum IdDistribution {
        /**
         * 均匀分布
         */
        UNIFORM,
        
        /**
         * Zipf分布（幂律分布，模拟热点）
         */
        ZIPF
    }
    
    /**
     * Zipf分布参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZipfParams {
        /**
         * Zipf参数s，值越大分布越陡峭（热点越集中）
         * 典型值: 1.0 ~ 2.0
         */
        @Builder.Default
        private double s = 1.1;
    }
    
    /**
     * 获取ID范围最小值
     */
    public long getIdMin() {
        return idRange != null && idRange.length > 0 ? idRange[0] : 1;
    }
    
    /**
     * 获取ID范围最大值
     */
    public long getIdMax() {
        return idRange != null && idRange.length > 1 ? idRange[1] : 1;
    }
    
    /**
     * 构建热点空间的快捷方法
     */
    public static KeySpaceConfig hotSpace(String name, String pattern, long min, long max, 
                                           double trafficShare, double zipfS) {
        return KeySpaceConfig.builder()
                .name(name)
                .pattern(pattern)
                .idRange(new long[]{min, max})
                .trafficShare(trafficShare)
                .idDistribution(IdDistribution.ZIPF)
                .zipfParams(ZipfParams.builder().s(zipfS).build())
                .build();
    }
    
    /**
     * 构建冷数据空间的快捷方法
     */
    public static KeySpaceConfig coldSpace(String name, String pattern, long min, long max,
                                            double trafficShare) {
        return KeySpaceConfig.builder()
                .name(name)
                .pattern(pattern)
                .idRange(new long[]{min, max})
                .trafficShare(trafficShare)
                .idDistribution(IdDistribution.UNIFORM)
                .build();
    }
}

