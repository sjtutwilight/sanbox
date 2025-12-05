package com.example.scheduler.executor;

import com.example.scheduler.controlplane.client.command.ExecutorCommand;
import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.experiment.CacheExperimentConfig;
import com.example.scheduler.experiment.Experiment;
import com.example.scheduler.experiment.ExperimentService;
import com.example.scheduler.experiment.OperationType;
import com.example.scheduler.experiment.scenario.ScenarioParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 根据实验定义填充统一命令的完整配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentCommandEnricher {

    private final ExperimentService experimentService;

    public ExecutorCommand enrich(ExecutorCommand command) {
        if (command.getOperationType() != null
                && (command.getDataRequest() != null
                || command.getReadConfig() != null
                || command.getMysqlInitConfig() != null
                || command.getRedisInitConfig() != null)) {
            return command;
        }
        Experiment.ExperimentOperation operation = experimentService.getOperation(
                command.getExperimentId(), command.getGroupId(), command.getOperationId());
        OperationType type = operation.getType();
        ExecutorCommand.ExecutorCommandBuilder builder = command.toBuilder()
                .operationType(type);

        switch (type) {
            case INIT_DATA -> builder.dataRequest(resolveDataRequest(operation, command.getOverrides()));
            case CONTINUOUS_WRITE -> builder.dataRequest(resolveDataRequest(operation, command.getOverrides()));
            case CONTINUOUS_READ -> builder.readConfig(resolveReadConfig(operation, command.getOverrides()))
                    .scenarioParams(copyScenarioParams(operation.getScenarioParams()));
            case INIT_MYSQL -> builder.mysqlInitConfig(resolveMysqlConfig(operation, command.getOverrides()));
            case INIT_REDIS -> builder.redisInitConfig(resolveRedisConfig(operation, command.getOverrides()));
        }
        return builder.build();
    }

    private DataGenerationRequest resolveDataRequest(Experiment.ExperimentOperation operation,
                                                     Map<String, Object> overrides) {
        DataGenerationRequest request = copyRequest(operation.getRequest());
        if (request == null) {
            throw new IllegalStateException("Experiment operation missing data request");
        }
        applyRequestOverrides(request, overrides);
        return request;
    }

    private Experiment.ReadLoadConfig resolveReadConfig(Experiment.ExperimentOperation operation,
                                                        Map<String, Object> overrides) {
        Experiment.ReadLoadConfig cfg = copyReadConfig(operation.getReadConfig());
        if (cfg == null) {
            cfg = new Experiment.ReadLoadConfig();
        }
        applyReadOverrides(cfg, overrides);
        return cfg;
    }

    private CacheExperimentConfig.MysqlInitConfig resolveMysqlConfig(Experiment.ExperimentOperation operation,
                                                                     Map<String, Object> overrides) {
        CacheExperimentConfig cacheConfig = operation.getCacheConfig();
        if (cacheConfig == null) {
            throw new IllegalStateException("Experiment operation missing cache config");
        }
        CacheExperimentConfig.MysqlInitConfig config = copyMysqlConfig(cacheConfig.getMysqlInit());
        if (config == null) {
            throw new IllegalStateException("Experiment operation missing mysql config");
        }
        applyMysqlOverrides(config, overrides);
        return config;
    }

    private CacheExperimentConfig.RedisInitConfig resolveRedisConfig(Experiment.ExperimentOperation operation,
                                                                     Map<String, Object> overrides) {
        CacheExperimentConfig cacheConfig = operation.getCacheConfig();
        if (cacheConfig == null) {
            throw new IllegalStateException("Experiment operation missing cache config");
        }
        CacheExperimentConfig.RedisInitConfig config = copyRedisConfig(cacheConfig.getRedisInit());
        if (config == null) {
            throw new IllegalStateException("Experiment operation missing redis config");
        }
        applyRedisOverrides(config, overrides);
        return config;
    }

    private void applyRequestOverrides(DataGenerationRequest request, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        if (overrides.containsKey("recordCount")) {
            request.setRecordCount(((Number) overrides.get("recordCount")).longValue());
        }
        if (overrides.containsKey("favPerUser")) {
            request.setFavPerUser(((Number) overrides.get("favPerUser")).intValue());
        }
        if (overrides.containsKey("valueSizeBytes")) {
            request.setValueSizeBytes(((Number) overrides.get("valueSizeBytes")).intValue());
        }
        if (overrides.containsKey("listWindow")) {
            request.setListWindow(((Number) overrides.get("listWindow")).intValue());
        }
        if (overrides.containsKey("batchSize")) {
            request.setBatchSize(((Number) overrides.get("batchSize")).intValue());
        }
        if (overrides.containsKey("qps")) {
            request.setQps(((Number) overrides.get("qps")).intValue());
        }
        if (overrides.containsKey("keyPrefix")) {
            request.setKeyPrefix((String) overrides.get("keyPrefix"));
        }
        if (overrides.containsKey("ttlSeconds")) {
            request.setTtlSeconds(((Number) overrides.get("ttlSeconds")).intValue());
        }
        if (overrides.containsKey("userCount")) {
            request.setUserCount(((Number) overrides.get("userCount")).longValue());
        }
        if (overrides.containsKey("symbolCount")) {
            long symbols = ((Number) overrides.get("symbolCount")).longValue();
            request.setSymbolCount((int) symbols);
            request.setRecordCount(symbols);
            request.setUserCount(symbols);
        }
        if (overrides.containsKey("candlesPerSymbol")) {
            request.setCandlesPerSymbol(((Number) overrides.get("candlesPerSymbol")).intValue());
        }
        if (overrides.containsKey("includeZset")) {
            request.setIncludeZset((Boolean) overrides.get("includeZset"));
        }
        if (overrides.containsKey("klineIntervals")) {
            Object raw = overrides.get("klineIntervals");
            List<String> intervals = normalizeIntervals(raw);
            if (!intervals.isEmpty()) {
                request.setKlineIntervals(intervals);
            }
        }
    }
    private List<String> normalizeIntervals(Object raw) {
        List<String> intervals = new ArrayList<>();
        if (raw == null) {
            return intervals;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                String value = item.toString().trim();
                if (!value.isEmpty()) {
                    intervals.add(value);
                }
            }
            return intervals;
        }
        String stringValue = raw.toString();
        if (stringValue.isBlank()) {
            return intervals;
        }
        for (String part : stringValue.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                intervals.add(value);
            }
        }
        return intervals;
    }

    private void applyReadOverrides(Experiment.ReadLoadConfig config, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty() || config == null) {
            return;
        }
        if (overrides.containsKey("keyPrefix")) config.setKeyPrefix((String) overrides.get("keyPrefix"));
        if (overrides.containsKey("userCount")) config.setUserCount(((Number) overrides.get("userCount")).intValue());
        if (overrides.containsKey("topN")) config.setTopN(((Number) overrides.get("topN")).intValue());
        if (overrides.containsKey("concurrency")) config.setConcurrency(((Number) overrides.get("concurrency")).intValue());
        if (overrides.containsKey("hotShare")) config.setHotShare(((Number) overrides.get("hotShare")).doubleValue());
        if (overrides.containsKey("idDistribution")) config.setIdDistribution((String) overrides.get("idDistribution"));
        if (overrides.containsKey("zipfS")) config.setZipfS(((Number) overrides.get("zipfS")).doubleValue());
        if (overrides.containsKey("qps")) config.setQps(((Number) overrides.get("qps")).intValue());
        if (overrides.containsKey("readMode")) {
            Object value = overrides.get("readMode");
            if (value instanceof String str && !str.isBlank()) {
                try {
                    config.setReadMode(Experiment.ReadMode.valueOf(str.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("无法解析 readMode={}，已忽略", str);
                }
            }
        }
        if (overrides.containsKey("cacheStrategy")) {
            Object value = overrides.get("cacheStrategy");
            if (value instanceof String str && !str.isBlank()) {
                try {
                    config.setCacheStrategy(Experiment.CacheStrategy.valueOf(str.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("无法解析 cacheStrategy={}，已忽略", str);
                }
            }
        }
    }

    private void applyMysqlOverrides(CacheExperimentConfig.MysqlInitConfig config, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty() || config == null) {
            return;
        }
        if (overrides.containsKey("userCount")) config.setUserCount(((Number) overrides.get("userCount")).longValue());
        if (overrides.containsKey("favPerUser")) config.setFavPerUser(((Number) overrides.get("favPerUser")).intValue());
        if (overrides.containsKey("batchSize")) config.setBatchSize(((Number) overrides.get("batchSize")).intValue());
        if (overrides.containsKey("truncateFirst")) config.setTruncateFirst((Boolean) overrides.get("truncateFirst"));
    }

    private void applyRedisOverrides(CacheExperimentConfig.RedisInitConfig config, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty() || config == null) {
            return;
        }
        if (overrides.containsKey("userCount")) config.setUserCount(((Number) overrides.get("userCount")).longValue());
        if (overrides.containsKey("favPerUser")) config.setFavPerUser(((Number) overrides.get("favPerUser")).intValue());
        if (overrides.containsKey("batchSize")) config.setBatchSize(((Number) overrides.get("batchSize")).intValue());
        if (overrides.containsKey("keyPrefix")) config.setKeyPrefix((String) overrides.get("keyPrefix"));
        if (overrides.containsKey("ttlSeconds")) {
            Object ttl = overrides.get("ttlSeconds");
            config.setTtlSeconds(ttl != null ? ((Number) ttl).intValue() : null);
        }
    }

    private long[] resolveUserRange(Long userCount) {
        long count = userCount != null && userCount > 0 ? userCount : 1;
        return new long[]{1, count};
    }

    private DataGenerationRequest copyRequest(DataGenerationRequest source) {
        if (source == null) {
            return null;
        }
        DataGenerationRequest target = new DataGenerationRequest();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private Experiment.ReadLoadConfig copyReadConfig(Experiment.ReadLoadConfig source) {
        if (source == null) {
            return null;
        }
        Experiment.ReadLoadConfig target = new Experiment.ReadLoadConfig();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private CacheExperimentConfig.MysqlInitConfig copyMysqlConfig(CacheExperimentConfig.MysqlInitConfig source) {
        if (source == null) {
            return new CacheExperimentConfig.MysqlInitConfig();
        }
        CacheExperimentConfig.MysqlInitConfig target = new CacheExperimentConfig.MysqlInitConfig();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private CacheExperimentConfig.RedisInitConfig copyRedisConfig(CacheExperimentConfig.RedisInitConfig source) {
        if (source == null) {
            return new CacheExperimentConfig.RedisInitConfig();
        }
        CacheExperimentConfig.RedisInitConfig target = new CacheExperimentConfig.RedisInitConfig();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private ScenarioParams copyScenarioParams(ScenarioParams source) {
        if (source == null) {
            return null;
        }
        ScenarioParams target = new ScenarioParams();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
