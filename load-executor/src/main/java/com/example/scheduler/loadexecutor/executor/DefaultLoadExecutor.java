package com.example.scheduler.loadexecutor.executor;

import com.example.scheduler.loadexecutor.config.LoadExecutorProperties;
import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.ExperimentRun;
import com.example.scheduler.loadexecutor.domain.LoadPhase;
import com.example.scheduler.loadexecutor.domain.LoadPlan;
import com.example.scheduler.loadexecutor.domain.RunMetrics;
import com.example.scheduler.loadexecutor.domain.RunStatus;
import com.example.scheduler.loadexecutor.datasource.redis.RedisStrategyResolver;
import com.example.scheduler.loadexecutor.experiment.ExperimentInvoker;
import com.example.scheduler.loadexecutor.experiment.ExperimentOperationHandle;
import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import com.example.scheduler.loadexecutor.experiment.config.ExperimentParameterOverrideService;
import com.example.scheduler.loadexecutor.generator.RequestPayloadGenerator;
import com.example.scheduler.loadexecutor.runtime.ExecutionTagSupport;
import com.example.scheduler.loadexecutor.runtime.RunExecutionContext;
import com.example.scheduler.loadexecutor.runtime.RunExecutionContextHolder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLoadExecutor implements LoadExecutor {

    private final RunRepository runRepository;
    private final ExperimentInvoker experimentInvoker;
    private final RequestPayloadGenerator payloadGenerator;
    private final ExperimentParameterOverrideService parameterOverrideService;
    private final LoadExecutorProperties properties;
    private final MeterRegistry meterRegistry;
    private final RedisStrategyResolver redisStrategyResolver;

    private final ConcurrentMap<String, RunContext> contexts = new ConcurrentHashMap<>();

    @Override
    public void start(ExperimentRun run, LoadPlan plan, ExperimentOperationHandle handle) {
        if (run == null || plan == null || handle == null) {
            throw new LoadExecutionException("Run, plan and handle are required");
        }
        RunContext context = new RunContext(run, plan, handle);
        RunContext existing = contexts.putIfAbsent(run.getId(), context);
        if (existing != null) {
            throw new LoadExecutionException("Run " + run.getId() + " is already running");
        }
        context.start();
    }

    @Override
    public void stop(String experimentRunId) {
        RunContext context = contexts.remove(experimentRunId);
        if (context != null) {
            context.stop(RunStatus.STOPPED, null);
        }
    }

    @Override
    public void pause(String experimentRunId) {
        RunContext context = contexts.get(experimentRunId);
        if (context != null) {
            context.pause();
        }
    }

    @Override
    public void resume(String experimentRunId) {
        RunContext context = contexts.get(experimentRunId);
        if (context != null) {
            context.resume();
        }
    }

    @Override
    public Optional<ExperimentRun> getRun(String experimentRunId) {
        RunContext context = contexts.get(experimentRunId);
        if (context != null) {
            return Optional.of(context.snapshot());
        }
        return runRepository.find(experimentRunId);
    }

    @Override
    public List<ExperimentRun> getAllRuns() {
        return runRepository.findAll();
    }

    private ThreadFactory schedulerFactory(String runId) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("load-scheduler-" + runId);
            thread.setDaemon(true);
            return thread;
        };
    }

    private ThreadFactory workerFactory(String runId) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("load-worker-" + runId);
            thread.setDaemon(true);
            return thread;
        };
    }

    private class RunContext {
        private final ExperimentRun initialRun;
        private final Command command;
        private final LoadPlan plan;
        private final ExperimentOperationHandle handle;
        private final ScheduledExecutorService scheduler;
        private final ThreadPoolExecutor workerPool;
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private final AtomicLong sequence = new AtomicLong(0);
        private final AtomicInteger inflight = new AtomicInteger(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
        private final AtomicLong maxLatencyMs = new AtomicLong(0);
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private final Counter successCounter;
        private final Counter failureCounter;
        private final Timer latencyTimer;
        private final AtomicReference<ExperimentRun> runHolder;
        private final long tickMillis;

        private volatile ScheduledFuture<?> future;
        private volatile Instant startedAt;
        private volatile LoadPhase lastPhase;
        private volatile double carryOver = 0;
        private volatile String lastError;

        RunContext(ExperimentRun run, LoadPlan plan, ExperimentOperationHandle handle) {
            this.initialRun = run;
            this.command = run.getCommand();
            this.plan = plan;
            this.handle = handle;
            int maxConcurrency = Math.max(plan.maxConcurrency(), properties.getDefaultMaxConcurrency());
            this.scheduler = Executors.newSingleThreadScheduledExecutor(schedulerFactory(run.getId()));
            this.workerPool = new ThreadPoolExecutor(
                    Math.min(properties.getWorker().getMinThreads(), maxConcurrency),
                    Math.min(properties.getWorker().getMaxThreads(), Math.max(maxConcurrency, properties.getWorker().getMinThreads())),
                    properties.getWorker().getKeepAliveSeconds(), TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(properties.getWorker().getQueueCapacity()),
                    workerFactory(run.getId()));
            this.workerPool.allowCoreThreadTimeOut(true);
            this.tickMillis = properties.getTickMillis();
            this.runHolder = new AtomicReference<>(run);
            this.successCounter = Counter.builder("load_executor_requests_total")
                    .tag("experimentId", safe(run.getCommand().getExperimentId()))
                    .tag("experimentRunId", run.getId())
                    .tag("operationId", safe(run.getCommand().getOperationId()))
                    .tag("platform", safe(run.getCommand().getPlatform()))
                    .tag("scenario", safe(run.getCommand().getScenario()))
                    .tag("outcome", "success")
                    .register(meterRegistry);
            this.failureCounter = Counter.builder("load_executor_requests_total")
                    .tag("experimentId", safe(run.getCommand().getExperimentId()))
                    .tag("experimentRunId", run.getId())
                    .tag("operationId", safe(run.getCommand().getOperationId()))
                    .tag("platform", safe(run.getCommand().getPlatform()))
                    .tag("scenario", safe(run.getCommand().getScenario()))
                    .tag("outcome", "error")
                    .register(meterRegistry);
            this.latencyTimer = Timer.builder("load_executor_request_latency_ms")
                    .tag("experimentId", safe(run.getCommand().getExperimentId()))
                    .tag("experimentRunId", run.getId())
                    .tag("operationId", safe(run.getCommand().getOperationId()))
                    .tag("platform", safe(run.getCommand().getPlatform()))
                    .tag("scenario", safe(run.getCommand().getScenario()))
                    .register(meterRegistry);
        }

        void start() {
            this.startedAt = Instant.now();
            ExperimentRun started = runHolder.updateAndGet(existing -> existing.markStarted(startedAt));
            runRepository.save(started.withMetrics(buildMetrics()));
            this.future = scheduler.scheduleAtFixedRate(this::tick, 0, tickMillis, TimeUnit.MILLISECONDS);
            log.info("Run {} started with {} phases", started.getId(), plan.getPhases().size());
        }

        void pause() {
            if (paused.compareAndSet(false, true)) {
                ExperimentRun pausedRun = runHolder.updateAndGet(run -> run.markStatus(RunStatus.PAUSED));
                runRepository.save(pausedRun);
                log.info("Run {} paused", pausedRun.getId());
            }
        }

        void resume() {
            if (paused.compareAndSet(true, false)) {
                ExperimentRun resumed = runHolder.updateAndGet(run -> run.markStatus(RunStatus.RUNNING));
                runRepository.save(resumed);
                log.info("Run {} resumed", resumed.getId());
            }
        }

        void stop(RunStatus targetStatus, String reason) {
            if (stopped.compareAndSet(false, true)) {
                if (future != null) {
                    future.cancel(true);
                }
                scheduler.shutdownNow();
                workerPool.shutdown();
                ExperimentRun ended = runHolder.updateAndGet(run -> run.markEnded(targetStatus, Instant.now(), reason));
                runRepository.save(ended.withMetrics(buildMetrics()));
                log.info("Run {} stopped with status {}", ended.getId(), targetStatus);
            }
        }

        void completeIfNeeded() {
            stop(RunStatus.COMPLETED, null);
            contexts.remove(initialRun.getId(), this);
        }

        ExperimentRun snapshot() {
            return runHolder.get().withMetrics(buildMetrics());
        }

        private void tick() {
            try {
                if (stopped.get()) {
                    return;
                }
                if (paused.get()) {
                    return;
                }
                if (startedAt == null) {
                    return;
                }
                Duration elapsed = Duration.between(startedAt, Instant.now());
                Optional<LoadPhase> phaseOptional = plan.phaseAt(elapsed);
                if (phaseOptional.isEmpty()) {
                    completeIfNeeded();
                    return;
                }
                LoadPhase phase = phaseOptional.get();
                if (lastPhase == null || lastPhase != phase) {
                    lastPhase = phase;
                    log.debug("Run {} entering phase {} with targetQps={} concurrency={}", initialRun.getId(), phase.getIndex(), phase.getTargetQps(), phase.getMaxConcurrency());
                }
                double ideal = phase.getTargetQps() * (tickMillis / 1000.0) + carryOver;
                int toDispatch = (int) Math.floor(ideal);
                carryOver = ideal - toDispatch;
                for (int i = 0; i < toDispatch; i++) {
                    if (!tryAcquireSlot(phase.getMaxConcurrency())) {
                        break;
                    }
                    dispatch(phase);
                }
                publishMetrics();
            } catch (Exception e) {
                log.error("Run {} tick failed", initialRun.getId(), e);
                lastError = e.getMessage();
            }
        }

        private boolean tryAcquireSlot(int maxConcurrency) {
            while (true) {
                int current = inflight.get();
                if (current >= maxConcurrency) {
                    return false;
                }
                if (inflight.compareAndSet(current, current + 1)) {
                    return true;
                }
            }
        }

        private void dispatch(LoadPhase phase) {
            long seq = sequence.incrementAndGet();
            try {
                CompletableFuture.runAsync(() -> execute(phase, seq), workerPool)
                        .whenComplete((ignored, throwable) -> inflight.decrementAndGet());
            } catch (Exception e) {
                inflight.decrementAndGet();
                failedRequests.incrementAndGet();
                lastError = e.getMessage();
                log.warn("Run {} scheduling rejected: {}", initialRun.getId(), e.getMessage());
            }
        }

        private void execute(LoadPhase phase, long sequence) {
            Instant scheduled = Instant.now();
            long startNs = System.nanoTime();
            boolean success = false;
            RunExecutionContext executionContext = buildExecutionContext();
            RunExecutionContextHolder.set(executionContext);
            try {
                Map<String, Object> payload = new HashMap<>();
                Map<String, Object> generated = payloadGenerator.nextPayload(command, phase, sequence);
                if (generated != null && !generated.isEmpty()) {
                    payload.putAll(generated);
                }
                Map<String, Object> overrides = parameterOverrideService.currentParameters(command);
                if (overrides != null && !overrides.isEmpty()) {
                    payload.putAll(overrides);
                }
                Map<String, Object> immutablePayload = payload.isEmpty()
                        ? Collections.emptyMap()
                        : Collections.unmodifiableMap(payload);
                OperationInvocationContext context = OperationInvocationContext.builder()
                        .command(command)
                        .phase(phase)
                        .payload(immutablePayload)
                        .platform(command.getPlatform())
                        .scenario(command.getScenario())
                        .experimentRunId(command.getExperimentRunId())
                        .sequence(sequence)
                        .scheduledAt(scheduled)
                        .startedAt(Instant.now())
                        .build();
                experimentInvoker.invoke(handle, context);
                success = true;
                successCounter.increment();
            } catch (Exception e) {
                failureCounter.increment();
                failedRequests.incrementAndGet();
                lastError = e.getMessage();
                log.warn("Run {} operation failed: {}", initialRun.getId(), e.getMessage());
            } finally {
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                latencyTimer.record(latencyMs, TimeUnit.MILLISECONDS);
                totalLatencyMs.addAndGet(latencyMs);
                maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
                totalRequests.incrementAndGet();
                if (success) {
                    successRequests.incrementAndGet();
                }
                RunExecutionContextHolder.clear();
            }
        }

        /**
         * 构造当前执行对应的 profile 上下文，供数据源路由和指标标签复用。
         */
        private RunExecutionContext buildExecutionContext() {
            return RunExecutionContext.builder()
                    .platform(command.getPlatform())
                    .scenario(command.getScenario())
                    .experimentRunId(command.getExperimentRunId())
                    .redisStrategy(redisStrategyResolver.resolve())
                    .build();
        }

        private void publishMetrics() {
            RunMetrics metrics = buildMetrics();
            runRepository.save(runHolder.updateAndGet(run -> run.withMetrics(metrics)));
        }

        private RunMetrics buildMetrics() {
            long total = totalRequests.get();
            double avgLatency = total == 0 ? 0 : (double) totalLatencyMs.get() / total;
            double elapsedSeconds = startedAt == null ? 0 : Math.max(1, Duration.between(startedAt, Instant.now()).getSeconds());
            double qps = total / elapsedSeconds;
            return RunMetrics.builder()
                    .totalRequests(total)
                    .successfulRequests(successRequests.get())
                    .failedRequests(failedRequests.get())
                    .avgLatencyMs(avgLatency)
                    .maxLatencyMs(maxLatencyMs.get())
                    .currentQps(qps)
                    .lastUpdated(Instant.now())
                    .build();
        }

        private String safe(String value) {
            return value != null ? value : "unknown";
        }
    }

}
