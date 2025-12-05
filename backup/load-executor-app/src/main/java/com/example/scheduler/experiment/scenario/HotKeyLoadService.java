package com.example.scheduler.experiment.scenario;

import com.example.scheduler.experiment.LoadTask;
import com.example.scheduler.experiment.LoadTaskStatus;
import com.example.scheduler.experiment.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 热点Key负载服务 - 执行基于Key空间分布的压测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotKeyLoadService {

    private final StringRedisTemplate redisTemplate;
    
    /**
     * 运行中的任务
     */
    private final Map<String, LoadTask> runningTasks = new ConcurrentHashMap<>();
    
    /**
     * 任务历史
     */
    private final Map<String, LoadTask> taskHistory = new ConcurrentHashMap<>();
    
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private String resolveExperimentRunId(String experimentRunId, String experimentId) {
        if (StringUtils.hasText(experimentRunId)) {
            return experimentRunId;
        }
        String fallback = StringUtils.hasText(experimentId) ? experimentId : "scenario";
        return fallback + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void bindMdc(LoadTask task) {
        if (task == null) {
            return;
        }
        if (StringUtils.hasText(task.getExperimentRunId())) {
            MDC.put("experimentId", task.getExperimentRunId());
        }
        if (StringUtils.hasText(task.getExperimentId())) {
            MDC.put("experimentTemplateId", task.getExperimentId());
        }
        if (StringUtils.hasText(task.getGroupId())) {
            MDC.put("groupId", task.getGroupId());
        }
        if (StringUtils.hasText(task.getOperationId())) {
            MDC.put("operationId", task.getOperationId());
        }
        if (StringUtils.hasText(task.getId())) {
            MDC.put("taskId", task.getId());
        }
    }

    private void clearMdc() {
        MDC.remove("experimentId");
        MDC.remove("experimentTemplateId");
        MDC.remove("groupId");
        MDC.remove("operationId");
        MDC.remove("taskId");
    }

    /**
     * 启动热点Key场景压测
     */
    public LoadTask startHotKeyLoad(String taskId, HotKeyScenarioConfig config, String experimentId, String experimentRunId) {
        if (runningTasks.containsKey(taskId)) {
            throw new IllegalStateException("该任务已在运行中");
        }
        String runId = resolveExperimentRunId(experimentRunId, experimentId);
        
        LoadTask task = LoadTask.builder()
                .id(taskId)
                .experimentId(experimentId)
                .experimentRunId(runId)
                .type(OperationType.CONTINUOUS_READ)
                .status(LoadTaskStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        
        runningTasks.put(taskId, task);
        taskHistory.put(taskId, task);
        log.info("启动热点压测 qps={} concurrency={} op={} experimentRunId={}",
                config.getLoad().getQps(), config.getLoad().getConcurrency(), config.getOperation().getType(), runId);
        
        executor.submit(() -> runHotKeyLoad(task, config));
        
        return task;
    }

    /**
     * 停止任务
     */
    public LoadTask stopTask(String taskId) {
        LoadTask task = runningTasks.get(taskId);
        if (task == null) {
            task = taskHistory.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("任务不存在: " + taskId);
            }
            return task;
        }
        task.requestStop();
        return task;
    }

    /**
     * 获取任务状态
     */
    public LoadTask getTask(String taskId) {
        LoadTask task = runningTasks.get(taskId);
        if (task == null) {
            task = taskHistory.get(taskId);
        }
        return task;
    }

    /**
     * 执行热点Key负载
     */
    private void runHotKeyLoad(LoadTask task, HotKeyScenarioConfig config) {
        bindMdc(task);
        try {
            log.info("启动热点Key压测任务: {}, config: {}", task.getId(), config);
            
            // 预处理：构建累积权重分布
            List<KeySpaceConfig> keySpaces = config.getKeySpaces();
            double[] cumulativeWeights = buildCumulativeWeights(keySpaces);
            
            // 为每个keySpace预生成Zipf采样器
            Map<String, ZipfSampler> zipfSamplers = new HashMap<>();
            for (KeySpaceConfig ks : keySpaces) {
                if (ks.getIdDistribution() == KeySpaceConfig.IdDistribution.ZIPF) {
                    double s = ks.getZipfParams() != null ? ks.getZipfParams().getS() : 1.1;
                    long range = ks.getIdMax() - ks.getIdMin() + 1;
                    zipfSamplers.put(ks.getName(), new ZipfSampler(range, s));
                }
            }
            
            LoadConfig load = config.getLoad();
            int concurrency = load.getConcurrency();
            int targetQps = load.getQps();
            int durationSeconds = load.getDurationSeconds();
            
            // 计算每个线程的间隔（纳秒）
            long intervalNanos = targetQps > 0 ? 
                    (long) (1_000_000_000.0 * concurrency / targetQps) : 0;
            
            ExecutorService workerPool = Executors.newFixedThreadPool(concurrency);
            List<Future<?>> futures = new ArrayList<>();
            
            long startTime = System.currentTimeMillis();
            long endTime = durationSeconds > 0 ? startTime + durationSeconds * 1000L : Long.MAX_VALUE;
            
            // 启动工作线程
            for (int i = 0; i < concurrency; i++) {
                futures.add(workerPool.submit(() -> {
                    bindMdc(task);
                    Random random = new Random();
                    
                    while (!task.shouldStop() && System.currentTimeMillis() < endTime) {
                        long opStart = System.nanoTime();
                        long id = -1;
                        
                        try {
                            // 1. 选择keySpace
                            KeySpaceConfig selectedSpace = selectKeySpace(keySpaces, cumulativeWeights, random);
                            
                            // 2. 在选中空间内采样ID
                            id = sampleId(selectedSpace, zipfSamplers.get(selectedSpace.getName()), random);
                            
                            // 3. 生成实际key
                            String key = selectedSpace.getPattern().replace("${id}", String.valueOf(id));
                            
                            // 4. 执行Redis操作
                            executeOperation(key, config.getOperation());
                            
                            task.incrementOps();
                            
                        } catch (Exception e) {
                            task.incrementErrors();
                            task.setLastError(e.getMessage());
                        }
                        
                        long opEnd = System.nanoTime();
                        double latencyMs = (opEnd - opStart) / 1_000_000.0;
                        updateLatencyStats(task, latencyMs);
                        
                        // QPS限速
                        if (intervalNanos > 0 && load.getMode() == LoadConfig.LoadMode.CONSTANT_QPS) {
                            long elapsed = opEnd - opStart;
                            if (elapsed < intervalNanos) {
                                try {
                                    Thread.sleep((intervalNanos - elapsed) / 1_000_000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    }
                    clearMdc();
                }));
            }
            
            // 主线程更新统计
            long lastStatTime = startTime;
            long lastOps = 0;
            
            while (!task.shouldStop() && System.currentTimeMillis() < endTime) {
                try {
                    Thread.sleep(1000);
                    
                    long now = System.currentTimeMillis();
                    long currentOps = task.getTotalOpsValue();
                    double opsPerSec = (currentOps - lastOps) * 1000.0 / (now - lastStatTime);
                    task.setCurrentOpsPerSec(opsPerSec);
                    lastOps = currentOps;
                    lastStatTime = now;
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // 停止所有工作线程
            workerPool.shutdownNow();
            task.setStatus(LoadTaskStatus.STOPPED);
            
        } catch (Exception e) {
            log.error("热点Key压测任务失败: {}", task.getId(), e);
            task.setStatus(LoadTaskStatus.FAILED);
            task.setLastError(e.getMessage());
        } finally {
            task.setEndedAt(Instant.now());
            runningTasks.remove(task.getId());
            clearMdc();
        }
    }

    /**
     * 构建累积权重分布
     */
    private double[] buildCumulativeWeights(List<KeySpaceConfig> keySpaces) {
        double[] weights = new double[keySpaces.size()];
        double cumulative = 0;
        for (int i = 0; i < keySpaces.size(); i++) {
            cumulative += keySpaces.get(i).getTrafficShare();
            weights[i] = cumulative;
        }
        return weights;
    }

    /**
     * 根据权重选择KeySpace
     */
    private KeySpaceConfig selectKeySpace(List<KeySpaceConfig> keySpaces, 
                                           double[] cumulativeWeights, Random random) {
        double r = random.nextDouble();
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (r < cumulativeWeights[i]) {
                return keySpaces.get(i);
            }
        }
        return keySpaces.get(keySpaces.size() - 1);
    }

    /**
     * 在选中的KeySpace内采样ID
     */
    private long sampleId(KeySpaceConfig space, ZipfSampler zipfSampler, Random random) {
        long min = space.getIdMin();
        long max = space.getIdMax();
        
        if (space.getIdDistribution() == KeySpaceConfig.IdDistribution.ZIPF && zipfSampler != null) {
            // Zipf分布采样，返回1-based的排名，转换为实际ID
            long rank = zipfSampler.sample(random);
            return min + rank - 1;
        } else {
            // 均匀分布
            return min + (long) (random.nextDouble() * (max - min + 1));
        }
    }

    /**
     * 执行Redis操作
     */
    private void executeOperation(String key, HotKeyScenarioConfig.OperationConfig operation) {
        String opType = operation.getType().toUpperCase();
        
        switch (opType) {
            case "GET" -> redisTemplate.opsForValue().get(key);
            case "SET" -> redisTemplate.opsForValue().set(key, generatePayload(operation.getValueSizeBytes()));
            case "HGETALL" -> redisTemplate.opsForHash().entries(key);
            case "ZREVRANGE" -> redisTemplate.opsForZSet().reverseRange(key, 0, operation.getTopN() - 1);
            case "ZRANGE" -> redisTemplate.opsForZSet().range(key, 0, operation.getTopN() - 1);
            case "SISMEMBER" -> redisTemplate.opsForSet().isMember(key, "test");
            case "SMEMBERS" -> redisTemplate.opsForSet().members(key);
            case "LRANGE" -> redisTemplate.opsForList().range(key, 0, operation.getTopN() - 1);
            default -> redisTemplate.opsForValue().get(key);
        }
    }

    private String generatePayload(int size) {
        if (size <= 0) size = 256;
        char[] chars = new char[size];
        Arrays.fill(chars, 'x');
        return new String(chars);
    }

    private synchronized void updateLatencyStats(LoadTask task, double latencyMs) {
        long ops = task.getTotalOpsValue();
        if (ops == 1) {
            task.setAvgLatencyMs(latencyMs);
            task.setMaxLatencyMs((long) latencyMs);
        } else {
            double currentAvg = task.getAvgLatencyMs();
            task.setAvgLatencyMs(currentAvg + (latencyMs - currentAvg) / ops);
            if (latencyMs > task.getMaxLatencyMs()) {
                task.setMaxLatencyMs((long) latencyMs);
            }
        }
    }

    /**
     * Zipf分布采样器
     */
    private static class ZipfSampler {
        private final long n;
        private final double s;
        private final double[] cumulativeProbabilities;

        public ZipfSampler(long n, double s) {
            this.n = Math.min(n, 100000); // 限制大小避免内存问题
            this.s = s;
            this.cumulativeProbabilities = new double[(int) this.n];
            
            // 计算归一化常数
            double harmonicSum = 0;
            for (int k = 1; k <= this.n; k++) {
                harmonicSum += 1.0 / Math.pow(k, s);
            }
            
            // 构建累积概率分布
            double cumulative = 0;
            for (int k = 1; k <= this.n; k++) {
                cumulative += (1.0 / Math.pow(k, s)) / harmonicSum;
                cumulativeProbabilities[k - 1] = cumulative;
            }
        }

        public long sample(Random random) {
            double r = random.nextDouble();
            // 二分查找
            int low = 0, high = cumulativeProbabilities.length - 1;
            while (low < high) {
                int mid = (low + high) / 2;
                if (cumulativeProbabilities[mid] < r) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            return low + 1;
        }
    }
}
