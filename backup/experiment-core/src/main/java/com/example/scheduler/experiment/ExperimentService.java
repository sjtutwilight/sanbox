package com.example.scheduler.experiment;

import com.example.scheduler.datagenerator.model.DataGenerationRequest;
import com.example.scheduler.datagenerator.model.GenerationPattern;
import com.example.scheduler.experiment.scenario.ScenarioParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.example.scheduler.datagenerator.model.DataDomain.*;
import static com.example.scheduler.datagenerator.model.DataSourceType.REDIS;
import static com.example.scheduler.datagenerator.model.GenerationPattern.*;

/**
 * 实验服务：管理预定义实验（只读）
 */
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final Map<String, Experiment> experiments = new LinkedHashMap<>();

    @PostConstruct
    public void initDefaultExperiments() {
        // 实验1: 活跃用户 Set vs Bitmap
        Experiment activeUsers = buildActiveUsersExperiment();
        activeUsers.setId("exp-active-users");
        assignGroupIds(activeUsers,
                "exp-active-users:set",
                "exp-active-users:bitmap");
        activeUsers.setArchitecture("Redis Set vs Bitmap，单实例 + Exporter + Grafana 观测命令延迟/内存。");
        activeUsers.setRiskWarnings(List.of(
                "BITCOUNT / SMEMBERS 在大 Key 上可能阻塞，注意 QPS 与范围。",
                "Set/Bitmap 空洞基数过大时，内存与扫描成本上升。"
        ));
        activeUsers.setMetricsToWatch(List.of(
                "redis_commands_latency_seconds_bucket{command=\"SISMEMBER\"/\"GETBIT\"}",
                "redis_memory_used_bytes",
                "redis_keyspace_hits_total / misses_total"
        ));
        experiments.put(activeUsers.getId(), activeUsers);

        // 实验2: 自选集合专项
        Experiment favorites = buildFavoritesExperiment();
        favorites.setId("exp-cache");
        assignGroupIds(favorites, "exp-cache:redis-mysql");
        favorites.setArchitecture("Redis Set+ZSet 按用户分散，MySQL 持久化（回源可选），Exporter+Grafana 观测 ops/延迟。");
        favorites.setRiskWarnings(List.of(
                "巨型 ZSet/Set 会导致命令阻塞，避免误用模式在线上环境。",
                "热点用户集中时注意连接池/线程池耗尽。"
        ));
        favorites.setMetricsToWatch(List.of(
                "redis_commands_latency_seconds_bucket{command=\"ZREVRANGE\"}",
                "redis_total_reads_processed / total_writes_processed",
                "pool/线程池队列长度（应用侧）"
        ));
        experiments.put(favorites.getId(), favorites);

        // 实验3: 用户持仓 Hash 对比
        Experiment positions = buildPositionsExperiment();
        positions.setId("exp-positions");
        assignGroupIds(positions,
                "exp-positions:per-user",
                "exp-positions:giant-hash");
        positions.setArchitecture("Redis Hash 按用户 vs 巨型 Hash，对比 HGETALL 行为，配合慢日志/Exporter。");
        positions.setRiskWarnings(List.of(
                "HGETALL 巨型 Hash 高风险，可能阻塞主线程。",
                "OBJECT encoding 可能随字段数变化，注意内存膨胀。"
        ));
        positions.setMetricsToWatch(List.of(
                "redis_commands_latency_seconds_bucket{command=\"HGETALL\"}",
                "redis_memory_used_bytes",
                "slowlog"
        ));
        experiments.put(positions.getId(), positions);

        // 实验4: 成交明细 List 对比
        Experiment trades = buildTradesExperiment();
        trades.setId("exp-trades");
        assignGroupIds(trades,
                "exp-trades:bounded-list",
                "exp-trades:unbounded-list");
        trades.setArchitecture("Redis List 有界 vs 无界，对比 LRANGE/LTRIM，观察内存与延迟。");
        trades.setRiskWarnings(List.of(
                "无界 List 会使 LRANGE O(N) 增长，触发慢查询。",
                "大 List 内存膨胀，注意 maxmemory 策略。"
        ));
        trades.setMetricsToWatch(List.of(
                "redis_commands_latency_seconds_bucket{command=\"LRANGE\"}",
                "redis_memory_used_bytes",
                "slowlog"
        ));
        experiments.put(trades.getId(), trades);

        // 实验5: 多周期K线缓存
        Experiment kline = buildKlineExperiment();
        kline.setId("exp-kline");
        assignGroupIds(kline,
                "exp-kline:bounded",
                "exp-kline:giant");
        kline.setArchitecture("前端按 1m/5m/1h/1d 多周期拉取 List，分析模块用 ZSet 做时间窗口；Exporter+Grafana 观察命令耗时。");
        kline.setRiskWarnings(List.of(
                "LRANGE 0 -1 针对无限增长的 List 属于 O(N) 大 Key 操作，极易触发卡顿/阻塞。",
                "不做 LTRIM 的 K 线缓存会持续吃掉内存，单个 symbol Key 可达几十 MB。",
                "策略回测用 LRANGE 而不是 ZRANGEBYSCORE，会把所有周期数据搬进客户端。"
        ));
        kline.setMetricsToWatch(List.of(
                "redis_commands_latency_seconds_bucket{command=\"LPUSH\"/\"LRANGE\"/\"ZRANGEBYSCORE\"}",
                "redis_slowlog_entries",
                "redis_memory_used_dataset_bytes",
                "keyspace_hits / misses & hit rate",
                "per-command stats: redis_cmdstat_lrange, redis_cmdstat_zrange"
        ));
        experiments.put(kline.getId(), kline);
    }

    /**
     * 获取所有实验列表
     */
    public List<Experiment> list() {
        return new ArrayList<>(experiments.values());
    }

    /**
     * 获取单个实验详情
     */
    public Experiment get(String id) {
        return Optional.ofNullable(experiments.get(id))
                .orElseThrow(() -> new IllegalArgumentException("实验不存在: " + id));
    }

    /**
     * 根据实验ID和组ID获取实验组
     */
    public Experiment.ExperimentGroup getGroup(String experimentId, String groupId) {
        Experiment exp = get(experimentId);
        return exp.getGroups().stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("实验组不存在: " + groupId));
    }

    /**
     * 获取操作定义
     */
    public Experiment.ExperimentOperation getOperation(String experimentId, String groupId, String operationId) {
        Experiment.ExperimentGroup group = getGroup(experimentId, groupId);
        return group.getOperations().stream()
                .filter(op -> op.getId().equals(operationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("操作不存在: " + operationId));
    }

    // ==================== 预定义实验构建 ====================

    private Experiment buildActiveUsersExperiment() {
        return Experiment.withAutoId(
                "活跃用户：Set vs Bitmap",
                "对比大规模活跃用户集合使用 Set 和 Bitmap 的存储与读取差异",
                "验证 Bitmap 在大规模基数场景下的内存优势，以及 Set 在小规模场景下的灵活性",
                List.of(
                        "内存占用对比：MEMORY USAGE key",
                        "读取性能：SISMEMBER vs GETBIT 响应时间",
                        "聚合查询：SCARD vs BITCOUNT 性能",
                        "Grafana 面板：redis_memory_used_bytes, redis_commands_processed_total"
                ),
                List.of(
                        "redis-cli SCARD exp:set:active_users:2025-01-01",
                        "redis-cli BITCOUNT exp:bitmap:active_users_bitmap:2025-01-01",
                        "redis-cli MEMORY USAGE exp:set:active_users:2025-01-01",
                        "redis-cli MEMORY USAGE exp:bitmap:active_users_bitmap:2025-01-01"
                ),
                List.of(
                        Experiment.ExperimentGroup.withAutoId(
                                "Set 方案",
                                "使用 Set 存储活跃用户ID，适合小规模场景和需要遍历成员的情况",
                                List.of(
                                        Experiment.ExperimentOperation.initData(
                                                "init",
                                                "初始化数据",
                                                "写入 50 万活跃用户到 Set，观察内存占用",
                                                buildActiveUsersSetRequest("exp:set:")
                                        ),
                                        Experiment.ExperimentOperation.continuousRead(
                                                "read",
                                                "持续读压测",
                                                "并发执行 SISMEMBER 检查用户是否活跃",
                                                Experiment.ReadLoadConfig.builder()
                                                        .keyPrefix("exp:set:")
                                                        .userCount(500000)
                                                        .topN(1)
                                                        .concurrency(16)
                                                        .readMode(Experiment.ReadMode.SET_ISMEMBER)
                                                        .build()
                                        )
                                )
                        ),
                        Experiment.ExperimentGroup.withAutoId(
                                "Bitmap 方案",
                                "使用 Bitmap 标记活跃用户，适合大规模基数场景，内存效率高",
                                List.of(
                                        Experiment.ExperimentOperation.initData(
                                                "init",
                                                "初始化数据",
                                                "写入 50 万活跃用户到 Bitmap，观察内存占用显著降低",
                                                buildActiveUsersBitmapRequest("exp:bitmap:")
                                        ),
                                        Experiment.ExperimentOperation.continuousRead(
                                                "read",
                                                "持续读压测",
                                                "并发执行 GETBIT 检查用户是否活跃",
                                                Experiment.ReadLoadConfig.builder()
                                                        .keyPrefix("exp:bitmap:")
                                                        .userCount(500000)
                                                        .topN(1)
                                                        .concurrency(16)
                                                        .readMode(Experiment.ReadMode.BITMAP_GETBIT)
                                                        .build()
                                        )
                                )
                        )
                )
        );
    }

    private Experiment buildFavoritesExperiment() {
        // MySQL初始化操作
        Experiment.ExperimentOperation initMysql = Experiment.ExperimentOperation.initMysql(
                "init-mysql",
                "初始化MySQL数据",
                "在MySQL中创建用户自选数据，作为缓存的数据源（userId从1开始）",
                CacheExperimentConfig.MysqlInitConfig.builder()
                        .userCount(100_000)
                        .favPerUser(200)
                        .batchSize(1000)
                        .truncateFirst(false)
                        .build()
        );
        initMysql.setConfigurable(true);
        
        // Redis初始化操作
        Experiment.ExperimentOperation initRedis = Experiment.ExperimentOperation.initRedis(
                "init-redis",
                "初始化Redis缓存",
                "在Redis中创建用户自选缓存（Set+ZSet），userId与MySQL保持一致",
                CacheExperimentConfig.RedisInitConfig.builder()
                        .userCount(100_000)
                        .favPerUser(200)
                        .batchSize(1000)
                        .keyPrefix("exp:cache:")
                        .ttlSeconds(null)  // 不设置TTL，用于测试淘汰策略
                        .build()
        );
        initRedis.setConfigurable(true);

        // 持续写压测（可选择write-through或直写Redis）
        Experiment.ExperimentOperation write = Experiment.ExperimentOperation.continuousWrite(
                "write",
                "持续写压测",
                "模拟用户持续添加自选，可选择write-through（同时写MySQL+Redis）或仅写Redis",
                buildFavNormalRequest("exp:cache:", 100_000, 10)
        );
        write.setConfigurable(true);
        write.getRequest().setWriteThrough(false);  // 默认仅写Redis
        write.getRequest().setUserCount(100_000L);

        // 持续读压测（支持热点配置和缓存策略）
        Experiment.ExperimentOperation read = Experiment.ExperimentOperation.continuousRead(
                "read",
                "持续读压测（缓存测试）",
                "支持热点配置、缓存策略（直读/Cache-aside），观察Redis命中率与MySQL回源压力",
                Experiment.ReadLoadConfig.builder()
                        .keyPrefix("exp:cache:")
                        .userCount(100_000)
                        .topN(50)
                        .concurrency(16)
                        .hotShare(0.8)
                        .idDistribution("zipf")
                        .zipfS(1.1)
                        .qps(5000)  // 默认5000 QPS
                        .readMode(Experiment.ReadMode.ZSET_RANGE)
                        .cacheStrategy(Experiment.CacheStrategy.CACHE_ASIDE)
                        .build()
        );
        read.setConfigurable(true);

        Experiment experiment = Experiment.withAutoId(
                "缓存实验：Redis+MySQL自选集合",
                "测试Redis作为MySQL缓存层的各种场景：命中率、雪崩、击穿、穿透",
                "通过调整maxmemory、淘汰策略、TTL等参数，观察缓存效果与MySQL回源压力",
                List.of(
                        "Redis命中率：keyspace_hits / (keyspace_hits + keyspace_misses)",
                        "MySQL回源QPS：观察Cache-aside模式下的MySQL查询压力",
                        "内存淘汰：evicted_keys 指标，测试maxmemory策略效果",
                        "热点Key延迟：ZREVRANGE在热点用户上的响应时间",
                        "缓存雪崩：大量Key同时过期时的MySQL压力",
                        "缓存击穿：热点Key失效瞬间的并发回源",
                        "缓存穿透：查询不存在的userId时的防护效果"
                ),
                List.of(
                        "# Redis缓存状态",
                        "redis-cli INFO stats | grep keyspace",
                        "redis-cli INFO memory | grep maxmemory",
                        "redis-cli CONFIG GET maxmemory-policy",
                        "",
                        "# 查看具体用户数据",
                        "redis-cli ZREVRANGE exp:cache:fav:z:1 0 49 WITHSCORES",
                        "redis-cli SISMEMBER exp:cache:fav:set:1 SYM100",
                        "",
                        "# MySQL数据验证",
                        "mysql -h localhost -u demo -pdemo123 experiment",
                        "SELECT COUNT(*) FROM favorite_symbol;",
                        "SELECT user_id, COUNT(*) FROM favorite_symbol GROUP BY user_id LIMIT 10;",
                        "",
                        "# 测试缓存淘汰",
                        "redis-cli CONFIG SET maxmemory 32mb",
                        "redis-cli CONFIG SET maxmemory-policy allkeys-lru"
                ),
                List.of(
                        Experiment.ExperimentGroup.withAutoId(
                                "Redis+MySQL组合",
                                "MySQL作为持久层，Redis作为缓存层，支持独立初始化和多种缓存策略",
                                List.of(initMysql, initRedis, write, read)
                        )
                )
        );
        experiment.setArchitecture("MySQL持久化 + Redis缓存（Set+ZSet），支持Cache-aside/直读，可调整maxmemory/淘汰策略");
        experiment.setRiskWarnings(List.of(
                "缓存雪崩：大量Key同时过期导致MySQL瞬时压力过大，建议设置随机TTL",
                "缓存击穿：热点Key失效时大量并发回源，建议使用分布式锁或永不过期策略",
                "缓存穿透：查询不存在的数据绕过缓存直击DB，建议使用布隆过滤器或缓存空值",
                "内存淘汰：maxmemory不足时触发淘汰，可能误删热点数据，注意监控evicted_keys"
        ));
        experiment.setMetricsToWatch(List.of(
                "Grafana MySQL Overview: http://localhost:3000/d/MQWgroiiz/mysql-overview",
                "Redis命中率：rate(redis_keyspace_hits_total[1m]) / (rate(redis_keyspace_hits_total[1m]) + rate(redis_keyspace_misses_total[1m]))",
                "MySQL QPS：rate(mysql_global_status_questions[1m])",
                "MySQL运行线程：mysql_global_status_threads_running（关注回源时的排队）",
                "MySQL慢查询：mysql_global_status_slow_queries",
                "Redis内存：redis_memory_used_bytes vs redis_memory_max_bytes",
                "Redis淘汰：rate(redis_evicted_keys_total[1m])",
                "Redis命令延迟：redis_commands_latency_seconds_bucket"
        ));
        return experiment;
    }
    
    /**
     * 构建热点Key场景默认参数
     */
    private ScenarioParams buildHotKeyScenarioParams() {
        return ScenarioParams.builder()
                .scenarioType("redis_hotkey")
                .keyPattern("exp:fav:normal:fav:z:${id}")
                        .hotShare(0.8)
                .idDistribution("zipf")
                .zipfS(1.1)
                .operationType("ZREVRANGE")
                .topN(50)
                .qps(2000)
                .concurrency(16)
                .durationSeconds(0)
                .build();
    }

    private Experiment buildPositionsExperiment() {
        return Experiment.withAutoId(
                "用户持仓：Hash 对比",
                "对比分散 Hash 与巨型 Hash 的性能差异",
                "理解 HGETALL 巨型 Hash 的风险，验证按用户分散 Key 的最佳实践",
                List.of(
                        "单 Key 大小：MEMORY USAGE key",
                        "读取性能：HGETALL 响应时间",
                        "阻塞风险：SLOWLOG 查看慢查询",
                        "Key 分布：SCAN 0 MATCH exp:hash:* COUNT 100"
                ),
                List.of(
                        "redis-cli HGETALL exp:hash:normal:pos:user:1",
                        "redis-cli HGETALL exp:hash:giant:pos:all",
                        "redis-cli MEMORY USAGE exp:hash:giant:pos:all",
                        "redis-cli SLOWLOG GET 10"
                ),
                List.of(
                        Experiment.ExperimentGroup.withAutoId(
                                "正常模式：每用户独立 Hash",
                                "每个用户一个 Hash Key，Key 分散，HGETALL 只读取单用户数据",
                                List.of(
                                        Experiment.ExperimentOperation.initData(
                                                "init",
                                                "初始化数据",
                                                "创建 50 万个用户 Hash，每个约 256 字节",
                                                buildPositionPerUserRequest("exp:hash:normal:")
                                        ),
                                        Experiment.ExperimentOperation.continuousRead(
                                                "read",
                                                "持续读压测",
                                                "并发执行 HGETALL，观察响应时间稳定",
                                                Experiment.ReadLoadConfig.builder()
                                                        .keyPrefix("exp:hash:normal:")
                                                        .userCount(500000)
                                                        .topN(1)
                                                        .concurrency(16)
                                                        .readMode(Experiment.ReadMode.HASH_GETALL)
                                                        .build()
                                        )
                                )
                        ),
                        Experiment.ExperimentGroup.withAutoId(
                                "误用模式：巨型 Hash",
                                "所有用户数据放入同一个 Hash，HGETALL 返回海量数据，高风险！",
                                List.of(
                                        Experiment.ExperimentOperation.initData(
                                                "init",
                                                "初始化数据",
                                                "警告：创建包含 50 万字段的巨型 Hash",
                                                buildPositionGiantRequest("exp:hash:giant:")
                                        ),
                                        Experiment.ExperimentOperation.continuousRead(
                                                "read",
                                                "持续读压测",
                                                "观察 HGETALL 大 Hash 导致的严重延迟",
                                                Experiment.ReadLoadConfig.builder()
                                                        .keyPrefix("exp:hash:giant:")
                                                        .userCount(1)
                                                        .topN(1)
                                                        .concurrency(4)
                                                        .readMode(Experiment.ReadMode.HASH_GETALL)
                                                        .build()
                                        )
                                )
                        )
                )
        );
    }

    private Experiment buildTradesExperiment() {
        return Experiment.withAutoId(
                "成交明细：List 对比",
                "验证有界 List 与无界 List 的性能差异",
                "理解 LRANGE 的 O(N) 复杂度，验证 LTRIM 保持窗口的重要性",
                List.of(
                        "List 长度：LLEN key",
                        "读取性能：LRANGE 0 49 响应时间",
                        "内存增长：随着写入持续观察内存变化",
                        "Slowlog：观察无界 List 的慢查询"
                ),
                List.of(
                        "redis-cli LLEN exp:list:normal:trades:BTCUSDT",
                        "redis-cli LLEN exp:list:giant:trades:BTCUSDT",
                        "redis-cli LRANGE exp:list:normal:trades:BTCUSDT 0 49",
                        "redis-cli TIME; redis-cli LRANGE exp:list:giant:trades:BTCUSDT 0 49; redis-cli TIME"
                ),
                List.of(
                        Experiment.ExperimentGroup.withAutoId(
                                "正常模式：有界 List (LTRIM)",
                                "使用 LTRIM 保持 List 最多 1000 条，内存稳定，读取快速",
                                List.of(
                                        Experiment.ExperimentOperation.initData(
                                                "init",
                                                "初始化数据",
                                                "写入 10 万条成交，但 LTRIM 只保留最新 1000 条",
                                                buildTradeNormalRequest("exp:list:normal:")
                                        ),
                                        Experiment.ExperimentOperation.continuousWrite(
                                                "write",
                                                "持续写压测",
                                                "持续写入成交，观察内存保持稳定",
                                                buildTradeNormalRequest("exp:list:normal:")
                                        ),
                                        Experiment.ExperimentOperation.continuousRead(
                                                "read",
                                                "持续读压测",
                                                "并发读取最新 50 条，响应时间稳定",
                                                Experiment.ReadLoadConfig.builder()
                                                        .keyPrefix("exp:list:normal:")
                                                        .userCount(1)
                                                        .topN(50)
                                                        .concurrency(16)
                                                        .readMode(Experiment.ReadMode.LIST_RANGE)
                                                        .build()
                                        )
                                )
                        ),
                        Experiment.ExperimentGroup.withAutoId(
                                "误用模式：无界 List",
                                "不使用 LTRIM，List 无限增长，LRANGE 越来越慢",
                                List.of(
                                        Experiment.ExperimentOperation.initData(
                                                "init",
                                                "初始化数据",
                                                "写入 100 万条成交，不裁剪，形成超长 List",
                                                buildTradeGiantRequest("exp:list:giant:")
                                        ),
                                        Experiment.ExperimentOperation.continuousWrite(
                                                "write",
                                                "持续写压测",
                                                "持续写入，观察内存持续增长",
                                                buildTradeGiantRequest("exp:list:giant:")
                                        ),
                                        Experiment.ExperimentOperation.continuousRead(
                                                "read",
                                                "持续读压测",
                                                "观察随着 List 增长，LRANGE 延迟上升",
                                                Experiment.ReadLoadConfig.builder()
                                                        .keyPrefix("exp:list:giant:")
                                                        .userCount(1)
                                                        .topN(50)
                                                        .concurrency(8)
                                                        .readMode(Experiment.ReadMode.LIST_RANGE)
                                                        .build()
                                        )
                                )
                        )
                )
        );
    }

    private Experiment buildKlineExperiment() {
        int symbolCount = 180;
        int boundedWindow = 1440; // 1 天 1m
        int boundedCandles = 2880; // 2 天 1m
        int giantCandles = 16000; // 故意堆积约 11 天

        DataGenerationRequest boundedInit = buildKlineRequest(
                "exp:kline:bounded:", symbolCount, boundedCandles, boundedWindow, KLINE_MULTI_WINDOW_BOUNDED, 172800);
        DataGenerationRequest giantInit = buildKlineRequest(
                "exp:kline:giant:", symbolCount, giantCandles, boundedWindow, KLINE_MULTI_WINDOW_GIANT, null);

        Experiment.ReadLoadConfig frontendRead = Experiment.ReadLoadConfig.builder()
                .keyPrefix("exp:kline:bounded:kline:list:1m:")
                .userCount(symbolCount)
                .topN(180)
                .concurrency(16)
                .hotShare(0.85)
                .idDistribution("zipf")
                .zipfS(1.05)
                .qps(1500)
                .readMode(Experiment.ReadMode.KLINE_LIST_RECENT)
                .build();

        Experiment.ReadLoadConfig boundedBacktest = Experiment.ReadLoadConfig.builder()
                .keyPrefix("exp:kline:bounded:kline:zset:1m:")
                .userCount(symbolCount)
                .topN(4320) // 3 days 1m
                .concurrency(4)
                .hotShare(0.4)
                .idDistribution("uniform")
                .qps(200)
                .readMode(Experiment.ReadMode.KLINE_ZSET_RANGE)
                .build();

        Experiment.ReadLoadConfig giantFrontend = Experiment.ReadLoadConfig.builder()
                .keyPrefix("exp:kline:giant:kline:list:1m:")
                .userCount(symbolCount)
                .topN(200)
                .concurrency(12)
                .hotShare(0.95)
                .idDistribution("zipf")
                .zipfS(1.08)
                .qps(800)
                .readMode(Experiment.ReadMode.KLINE_LIST_RECENT)
                .build();

        Experiment.ReadLoadConfig giantReplay = Experiment.ReadLoadConfig.builder()
                .keyPrefix("exp:kline:giant:kline:list:1m:")
                .userCount(symbolCount)
                .topN(0) // 0 代表 LRANGE 0 -1
                .concurrency(6)
                .hotShare(0.7)
                .idDistribution("zipf")
                .zipfS(1.05)
                .qps(150)
                .readMode(Experiment.ReadMode.KLINE_LIST_RECENT)
                .build();

        Experiment.ExperimentOperation boundedInitOp = Experiment.ExperimentOperation.initData(
                "init",
                "初始化多周期 K 线",
                "为 180 个交易对生成 1m/5m/1h/1d K 线，并裁剪至 1 天窗口",
                boundedInit
        );
        boundedInitOp.setConfigurable(true);

        Experiment.ExperimentOperation boundedFrontendOp = Experiment.ExperimentOperation.continuousRead(
                "frontend",
                "前端实时刷新",
                "模拟前端每次拉最新 180 根 1m K 线，Zipf 热点集中在头部交易对",
                frontendRead
        );
        boundedFrontendOp.setConfigurable(true);

        Experiment.ExperimentOperation boundedBacktestOp = Experiment.ExperimentOperation.continuousRead(
                "backtest",
                "策略回测（正确用法）",
                "回测接口使用 ZSet 分数（毫秒时间戳）提取最近 3 天 1m 数据，避免大 Key LRANGE",
                boundedBacktest
        );
        boundedBacktestOp.setConfigurable(true);

        Experiment.ExperimentOperation giantInitOp = Experiment.ExperimentOperation.initData(
                "init",
                "初始化并累积全量 K 线",
                "故意去掉 LTRIM/TTL，把所有历史都塞在 List/ZSet，方便复现 big key",
                giantInit
        );
        giantInitOp.setConfigurable(true);

        Experiment.ExperimentOperation giantFrontendOp = Experiment.ExperimentOperation.continuousRead(
                "frontend",
                "前端误用",
                "同样只想取 200 根，但因为 List 巨大，LRANGE 仍需扫描大量节点",
                giantFrontend
        );
        giantFrontendOp.setConfigurable(true);

        Experiment.ExperimentOperation giantReplayOp = Experiment.ExperimentOperation.continuousRead(
                "replay-all",
                "策略回测（错误）",
                "模拟“LRANGE 0 -1”接口高频调用，对最后一个 symbol 造成长时间阻塞",
                giantReplay
        );
        giantReplayOp.setConfigurable(true);

        return Experiment.withAutoId(
                "多周期 K 线缓存：List + ZSet",
                "模拟撮合/行情服务把 1m / 5m / 1h / 1d K 线写入 Redis，同时提供前端实时窗口和策略回测接口",
                "验证 LTRIM 保持最新窗口的必要性，并对比 ZSet 范围查询与“LRANGE 0 -1”大 Key 误用造成的延迟/内存暴涨",
                List.of(
                        "多周期窗口长度：LLEN / ZCARD 观测 1m vs 1h Key 大小",
                        "命令延迟：redis_cmdstat_lrange / zrange vs 写入 LPUSH",
                        "LRANGE 0 -1 是否触发慢查询/阻塞线程",
                        "ZSet 按时间戳回溯是否稳定，观察 hits/misses 与 keyspace statistics",
                        "内存增长：MEMORY USAGE exp:kline:* 与 used_dataset"
                ),
                List.of(
                        "redis-cli LLEN exp:kline:bounded:kline:list:1m:sym:00001",
                        "redis-cli LRANGE exp:kline:bounded:kline:list:1m:sym:00001 0 199",
                        "redis-cli ZREVRANGEBYSCORE exp:kline:bounded:kline:zset:1m:sym:00001 +inf -inf LIMIT 0 4320",
                        "redis-cli LLEN exp:kline:giant:kline:list:1m:sym:00001",
                        "redis-cli LRANGE exp:kline:giant:kline:list:1m:sym:00001 0 -1 | head",
                        "redis-cli MEMORY USAGE exp:kline:giant:kline:list:1m:sym:00001",
                        "redis-cli SLOWLOG GET 5"
                ),
                List.of(
                        Experiment.ExperimentGroup.withAutoId(
                                "正常：多周期窗口裁剪",
                                "LPUSH + LTRIM 保持固定窗口，并用 ZSet 存放时间戳，策略回测走 ZRANGEBYSCORE",
                                List.of(
                                        boundedInitOp,
                                        boundedFrontendOp,
                                        boundedBacktestOp
                                )
                        ),
                        Experiment.ExperimentGroup.withAutoId(
                                "误用：无限累积 K 线",
                                "所有周期都不做 LTRIM/TTL，策略接口直接 LRANGE 0 -1，形成典型 big key + O(N)",
                                List.of(
                                        giantInitOp,
                                        giantFrontendOp,
                                        giantReplayOp
                                )
                        )
                )
        );
    }

    // ==================== Request 构建辅助方法 ====================

    private DataGenerationRequest buildActiveUsersSetRequest(String keyPrefix) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(ACTIVE_USERS);
        req.setPattern(ACTIVE_USERS_DAILY_SET);
        req.setRecordCount(500_000L);
        req.setKeyPrefix(keyPrefix);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        return req;
    }

    private DataGenerationRequest buildActiveUsersBitmapRequest(String keyPrefix) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(ACTIVE_USERS);
        req.setPattern(ACTIVE_USERS_BITMAP);
        req.setRecordCount(500_000L);
        req.setKeyPrefix(keyPrefix);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        return req;
    }

    private DataGenerationRequest buildFavNormalRequest(String keyPrefix, int userCount, int favPerUser) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(FAVORITE);
        req.setPattern(FAV_NORMAL_SET_ZSET);
        req.setRecordCount((long) userCount);
        req.setKeyPrefix(keyPrefix);
        req.setFavPerUser(favPerUser);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        req.setBatchSize(1000);
        return req;
    }

    private DataGenerationRequest buildFavGiantRequest(String keyPrefix, int userCount, int favPerUser) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(FAVORITE);
        req.setPattern(FAV_GIANT_SET_ZSET);
        req.setRecordCount((long) userCount);
        req.setKeyPrefix(keyPrefix);
        req.setFavPerUser(favPerUser);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        req.setBatchSize(500);
        return req;
    }

    private DataGenerationRequest buildPositionPerUserRequest(String keyPrefix) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(USER_POSITION);
        req.setPattern(USER_POSITION_PER_USER_HASH);
        req.setRecordCount(500_000L);
        req.setValueSizeBytes(256);
        req.setKeyPrefix(keyPrefix);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        return req;
    }

    private DataGenerationRequest buildPositionGiantRequest(String keyPrefix) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(USER_POSITION);
        req.setPattern(USER_POSITION_GIANT_HASH);
        req.setRecordCount(500_000L);
        req.setValueSizeBytes(256);
        req.setKeyPrefix(keyPrefix);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        return req;
    }

    private DataGenerationRequest buildTradeNormalRequest(String keyPrefix) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(TRADE_FEED);
        req.setPattern(TRADE_RECENT_LIST);
        req.setRecordCount(100_000L);
        req.setValueSizeBytes(128);
        req.setKeyPrefix(keyPrefix);
        req.setListWindow(1000);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        return req;
    }

    private DataGenerationRequest buildTradeGiantRequest(String keyPrefix) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(TRADE_FEED);
        req.setPattern(TRADE_HISTORY_LIST);
        req.setRecordCount(1_000_000L);
        req.setValueSizeBytes(128);
        req.setKeyPrefix(keyPrefix);
        req.setTtlSeconds(3600);
        req.setOverwrite(true);
        return req;
    }

    private DataGenerationRequest buildKlineRequest(String keyPrefix,
                                                    int symbolCount,
                                                    int candlesPerSymbol,
                                                    int listWindow,
                                                    GenerationPattern pattern,
                                                    Integer ttlSeconds) {
        DataGenerationRequest req = new DataGenerationRequest();
        req.setDataSource(REDIS);
        req.setDomain(KLINE);
        req.setPattern(pattern);
        req.setRecordCount((long) symbolCount);
        req.setUserCount((long) symbolCount);
        req.setSymbolCount(symbolCount);
        req.setCandlesPerSymbol(candlesPerSymbol);
        req.setListWindow(listWindow);
        req.setKeyPrefix(keyPrefix);
        req.setKlineIntervals(List.of("1m", "5m", "1h", "1d"));
        req.setIncludeZset(Boolean.TRUE);
        req.setTtlSeconds(ttlSeconds);
        req.setValueSizeBytes(256);
        req.setBatchSize(32);
        req.setOverwrite(true);
        return req;
    }

    private void assignGroupIds(Experiment experiment, String... ids) {
        if (experiment.getGroups() == null) {
            return;
        }
        for (int i = 0; i < experiment.getGroups().size(); i++) {
            Experiment.ExperimentGroup group = experiment.getGroups().get(i);
            if (group == null || ids == null || i >= ids.length) {
                continue;
            }
            group.setId(ids[i]);
        }
    }
}
