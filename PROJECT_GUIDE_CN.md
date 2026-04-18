# 项目导读（Load Executor + Control Plane + 前端实验台）

## 1. 愿景与当前范围
- **目标**：把 `load-executor` 打造成可配置的“小型压测平台”，由控制面统一发命令、承载多实验场景（缓存、自选、钱包、Kafka、ConcurrentHashMap 等），并在 Grafana/Loki 上形成可重现的观测闭环。
- **当前结果**：形成了三层解耦架构——React 实验台 → Spring 控制面 (`control-plane-app`) → Spring 负载执行器 (`load-executor`)。`docker-compose.yml` 则一次性拉起 Redis/MySQL/Kafka/Flink/Nacos + Prometheus/Grafana/Loki/Exporter 观测集群。
- **后续重点**：沉淀统一命令模型、让实验实现像插件一样注册，同时把 Nacos 动态参数、Grafana Dashboard、日志聚合流程模板化，方便下一批同事/agent 无缝扩展。

## 2. 顶层架构与数据流
1. 前端 (`frontend/src`) 通过 `/api/experiments` 拉取元数据，展示 operation、默认 LoadShape、参数提示，并提供启动/停止按钮。
2. 控制面 (`control-plane-app`) 的 `ExperimentController` 将 UI 请求下发给 `ExperimentOperationCoordinator`，补齐 `ExecutorCommand` 信息（experimentId、operationId、overrides、loadShape）。
3. `RunOrchestrator` 通过 `RemoteLoadExecutorClient` 调用负载执行器 `POST /commands`，并把返回的 `ExperimentRunResponse` 映射为 `LoadTask`（含状态、QPS、延迟、误差）。
4. `load-executor`：
   - `CommandController` 校验 HTTP 请求 → `CommandServiceImpl` 把 DTO 转成领域模型 `Command` + `LoadShape`。
   - `LoadPlanner` 根据 `LoadShape` 生成 `LoadPlan`（多阶段 `LoadPhase`）。
   - `DefaultLoadExecutor` 为每个 run 创建 `RunContext`、调度循环和 worker 线程池，通过 `ExperimentInvoker` 调用具体 experiment。
   - `InMemoryRunRepository` + `RunController` 提供 `/runs` 查询，`ExperimentMetadataController` 回传 experiment 描述给控制面。
5. 动态参数：`CommandServiceImpl` 把 overrides 同步到 `NacosExperimentParameterOverrideService`，后者通过 `ExperimentParameterOverrideService` 被执行器读取，实现运行期调参。
6. 观测：Micrometer 指标输出至 Prometheus → Grafana Dashboard；日志通过 Promtail → Loki，控制面 `LogController` 转发查询；前端 `GrafanaPanels` 组件嵌入 iframe。

## 3. 模块详解

### 3.1 `load-executor` 核心
- **领域模型**（`load-executor/src/main/java/com/example/scheduler/loadexecutor/domain`）  
  `Command`（控制面的单次请求）、`LoadShape/LoadPlan/LoadPhase`（负载曲线）、`ExperimentRun/RunStatus/RunMetrics`（生命周期 + 观测数据）。
- **API 层**  
  `CommandController` 暴露 `/commands` `POST` / `/{runId}/stop|pause|resume`；`RunController` 暴露 `/runs`；`ExperimentMetadataController` 提供 `/experiments` 列表供控制面/前端使用。统一通过 `RunResponseMapper` 整理返回体。
- **CommandService**（`CommandServiceImpl`）  
  负责校验 experiment 是否存在、构造 `LoadShape`、写入 `RunRepository`、调用 `LoadPlanner` 和 `LoadExecutor`。`syncDynamicConfig` 在提交命令时把 overrides 写入 Nacos，保证后续 run 可复用。
- **LoadPlanner**  
  `DefaultLoadPlanner` 支持 `constant/hot_key/ramp/burst` 4 种 LoadShape，把 `(qps, concurrency, duration, params)` 转成多个 `LoadPhase`。`hot_key` 会解析 `HotKeyConfig`（热点 key 数量/占比/空间/前缀）。
- **执行引擎**（`DefaultLoadExecutor`）  
  - 每个 run 建立 `RunContext`：调度线程 + worker 线程池，定时 tick（默认 100ms）→ 读取当前 phase → 计算本 tick 需派发的请求数 → 提交到 worker。  
  - worker 内部通过 `RequestPayloadGenerator` 生成 payload，叠加 Nacos overrides，再交给 `ExperimentInvoker` 调用。  
  - 运行指标 (`RunMetrics`) 与 Micrometer Counter/Timer（成功、失败、latency）在 `RunContext.publishMetrics()` 中持续更新，`/runs` 查询即可看到最新 QPS/延迟/错误数。  
  - 支持 `pause/resume/stop`，到期后自动 `COMPLETED` 并回收上下文。
- **Experiment Registry**  
  `DefaultExperimentRegistry` 在 Spring 启动时扫描所有 `ExperimentGroup`，构造 `ExperimentDescriptor` 并建立 `(experimentId, groupId, operationId)` → `ExperimentOperationHandle` 映射，供控制面发现与执行器调用。
- **实验实现（节选）**  
  - `favorite/default`：读写用户自选（Redis + MySQL）。`FavoriteExperimentGroup` 暴露 read/write/warm 四个 operation，并通过 `FavoritePayloadTemplate` 生成 userId/symbol。  
  - `wallet_query/default`：含 `query_snapshot`、`warm_snapshot`、`rebuild_ledger`、`publish_bus`，覆盖 Redis 快照缓存 + MySQL ledger + Kafka 事件。  
  - `metadata_cache/default` & `udqs_plan/default`：使用 `ConcurrentHotCacheEngine` 模拟 `ConcurrentHashMap` 热点访问、失效、重建，方便调参（`ConcurrentCacheSettings`）。  
  - `kafka_kline/market-data`：`KafkaKlineExperimentService` 组合 symbol 权重生成 Binance 风格 K 线并投递 Kafka。  
  - `experiment/concurrentmap`/`wallet`/`favorite` 等包提供丰富参数定义（`OperationParameter`）与默认 LoadShape（`LoadShapeTemplate`），控制面会读取这些元数据填充 UI。
- **公共能力**  
  - 数据源抽象：`datasource/redis` 与 `datasource/mysql` 包装 Micrometer 指标、错误处理，experiment 只需写 Repository。  
  - 数据生成器：`datagenerator/DataGeneratorController` 提供 `/datagenerator/favorite`，可批量写 MySQL + 预热缓存。  
  - 动态参数：`NacosExperimentParameterOverrideService` 监听 `experiment.dynamic-config` 配置并实时推送给执行器。  
  - payload 模板：`generator/template` 中按 experiment 生成请求体，便于不同 operation 共用字段。

### 3.2 `control-plane-app`
- **Experiment 元数据同步**  
  `ExperimentService` 调用 `LoadExecutorClient.listExperiments()`，把 `ExperimentDescriptorResponse` 转成控制面内部模型（实验 → 分组 → 操作 → 参数 → 默认 LoadShape）。前端直接消费 `/api/experiments`。
- **Run 编排**  
  `ExperimentOperationCoordinator` 组装 `ExecutorCommand`，`RunOrchestrator` 负责 runId 生成、去重（`lastRunByTask`）、状态缓存以及与 `RemoteLoadExecutorClient` 的交互：  
  - `submit()` → `/commands`；`stop()` → `/commands/{runId}/stop`；`getRun()` → `/runs/{runId}`；  
  - `listRuns()` → `/runs`，再筛选出 RUNNING 状态供“运行中任务”面板展示。
- **观测与日志 API**  
  `ObservabilityController` 根据配置生成 Grafana iframe/external URL；`LogController` 充当 Loki 代理，可按 `experimentId` 拉取 app log + docker error 聚合，前端 `ExperimentLogModal` 直接使用。
- **数据生成/配置**  
  `ConfigController` 汇总数据源/业务域/生成模式/默认值（供 Data Generator UI 使用）；`datagenerator` 包含任务模型、状态机与 `/api/data-generator/jobs` 接口。
- **配置与属性**  
  `LoadExecutorProperties` 描述控制面访问执行器的 base url、超时、默认 QPS/并发；`ObservabilityProperties` 管理 Loki/Grafana endpoint、dashboard 列表、默认查询范围等。

### 3.3 前端（React + Vite）
- `src/App.jsx` 组织 Sidebar + 主内容；`ExperimentPage` 负责实验列表、操作卡片（`ExperimentCard`）、Grafana 嵌入、日志弹窗；`DataGenPage` 对接数据生成。
- `hooks/useApi.js` 统一封装 `/api` 请求，含实验、配置、数据生成、观测等 API，自动附带 `X-Experiment-Id` header。
- Grafana 嵌入：`GrafanaPanel` 调用控制面 `/api/observability/grafana/embed-url`，根据 `experimentRunId` 透传变量（如 `var-experimentRunId`）筛选 dashboard。
- UI 默认代理 `/api` 到 `http://localhost:8080`（控制面端口），详见 `vite.config.js`。

### 3.4 基础设施与观测栈
- `docker-compose.yml` 一次性启动：Redis(含 Insight/Exporter)、MySQL(含 Exporter)、Kafka(带 JMX agent + Kafka UI + exporter)、Prometheus、Grafana、Loki/Promtail、Flink JobManager/TaskManager、Nacos。默认端口：Redis 6379、MySQL 3306、Kafka 9092/29092、Grafana 3000、Prometheus 9090、Loki 3100、Nacos 8848/9848。
- Grafana 默认加载 `grafana/provisioning/dashboards` 下的 Kafka/Flink/Concurrent Cache 等面板。Prometheus job 配置在 `prometheus/prometheus.yml`。
- 日志路径 `logs/app` 已挂到 Promtail。`backend.log`/`frontend.log` 便于排查本地启动问题。

## 4. 运行与调试
1. 启动依赖：`docker compose up -d redis mysql kafka grafana prometheus loki promtail nacos`（或 `docker compose up -d` 全拉起）。
2. 启动 load executor：`./run-load-executor.sh`（可用 `JVM_ARGS` 覆盖堆、GC 参数；开放 `EXPERIMENT_DYNAMIC_CONFIG_*` 环境变量启用 Nacos）。
3. 启动控制面：`./run-control-plane.sh`（监听 8080）。前端若以 dev 模式运行则代理到此端口。
4. 前端：`cd frontend && npm install && npm run dev`（默认 5173），或 `npm run build` 后用任何静态服务器托管 `dist/`。
5. API 验证：  
   - `curl -s http://localhost:9091/experiments`（load executor）  
   - `curl -s http://localhost:8080/api/experiments`（控制面）  
   - `curl -X POST http://localhost:9091/commands -d '{...}'` 直接压测。
6. 观测：Grafana (admin/admin) → Dashboard（Kafka Observability / Flink / Concurrent Cache / Load Executor）；Loki 查询 `experiment_id="<runId>"`；Kafka UI `http://localhost:8085`。

## 5. 扩展与演进建议
1. **实验插件化**：在 `load-executor/.../experiment` 新增 `ExperimentGroup` 即可自动注册；建议同时提供 `LoadShapeTemplate`、`OperationParameter`，并更新前端 `constants.js` 的提示。
2. **动态参数治理**：Nacos 目前简单写入 JSON，可扩展 diff/版本化/灰度（在 `ExperimentDynamicConfigPublisher` 增加审计字段）。
3. **多租户/隔离**：现阶段 `RunContext` 只按 runId 管理，可引入配额（线程池/Redis/MySQL 连接限额）或按实验打标签，避免不同实验互相影响。
4. **更丰富的指标**：`DefaultLoadExecutor` 已打了成功/失败/latency，可继续补充 queue length、inflight、payload size；experiment 侧通过 Micrometer Gauge 观测业务指标（缓存命中率、Kafka lag）。
5. **控制面能力**：在 `RunOrchestrator` 加入调度策略（批量启动/编排依赖）、在 `ExperimentController` 增加“一次提交多个 operation”、“存储实验模板”等。
6. **前端体验**：支持自定义 Grafana Dashboard 绑定、拖拽排序、Nacos 覆盖 diff 预览、Experiment Run 历史列表等。
7. **数据生成器**：目前只有 favorite，可以参考 `FavoriteDataGeneratorService` 扩展 wallet/concurrent map 场景，或接入 Kafka 造数。

## 6. 常用接口速查
| 服务 | 端点 | 说明 |
| --- | --- | --- |
| load-executor | `GET /experiments` | 实验元数据，供控制面同步 |
|  | `POST /commands` | 提交 run（`CommandRequest`） |
|  | `POST /commands/{runId}/stop|pause|resume` | 控制 run |
|  | `GET /runs` / `/runs/{runId}` | 查询 run 状态/metrics |
|  | `GET /actuator/prometheus` | Micrometer 指标 |
| control-plane | `GET /api/experiments` | 前端用来渲染实验 |
|  | `POST /api/experiments/{exp}/groups/{group}/operations/{op}/start` | 通过控制面启动 operation |
|  | `POST /api/experiments/.../stop` | 停止 |
|  | `GET /api/observability/grafana/embed-url` | Grafana iframe 链接 |
|  | `GET /api/logs?experimentId=...` | Loki 日志汇总 |
|  | `POST /api/data-generator/jobs` | 创建数据生成任务 |
| 前端 | `/api/*` | 由 Vite proxy 转发到控制面 |

## 7. 实验场景与代码入口
- **自选缓存 (`favorite/default`)**：  
  - 读：`FavoriteExperimentGroup.read_cache_aside`、`FavoriteExperimentService.readWithCache`。  
  - 写：`add_favorite`/`remove_favorite` → MySQL + Redis DEL。  
  - 预热：`warm_cache`。  
  - 数据模型：`mysql/init/favorite_symbol.sql`。
- **钱包查询 (`wallet_query/default`)**：  
  - `WalletQueryExperimentService.querySnapshot/warmSnapshots/rebuildLedger/publishBus`，涵盖 Redis、MySQL、Kafka。  
  - 通过 `WalletSnapshotBuilder` 调整 assetCount/history/risk/fillerBytes，复现 Heap/CPU 场景。
- **ConcurrentHashMap 调优 (`metadata_cache/default`, `udqs_plan/default`)**：  
  - `ConcurrentHotCacheEngine` + `ConcurrentCacheRequest` 模拟热点读、批量失效/重建，有完备的 Micrometer 指标 `concurrent_cache_*`。  
  - 参数可由 Nacos 或前端 overrides 实时调整。
- **Kafka K 线 (`kafka_kline/market-data`)**：  
  - `KafkaKlineExperimentService` 生成 payload、通过 `KafkaTemplate` 写入 `binance.kline`。  
  - `KafkaKlineRequest` 支持权重/interval/hot key/variance。
- **其他实践**：`datagenerator/FavoriteDataGeneratorService` 造数、`experiment/support/RequestSupport` 统一解析 payload。

## 8. 故障排查速记
- Run 无响应：查看 `load-executor` 控制台日志或 `GET /runs/{runId}` `lastError` 字段；如线程池拒绝任务，调大 `load.executor.worker.queueCapacity`。
- 控制面无法启动 run：检查 `RemoteLoadExecutorClient` 的 `load-executor.base-url`，或确认 `load-executor` `POST /commands` 权限。
- Grafana 无法嵌入：确保 `grafana.ini` 允许匿名/iframe（compose 已配置），或通过 `ObservabilityProperties` 修改 URL。
- Nacos 参数不生效：确认 `EXPERIMENT_DYNAMIC_CONFIG_ENABLED=true`，并在 Nacos `EXPERIMENT/load-executor-experiment-params.json` 中填入合法 JSON；日志会打印加载条目数。
- Kafka 连接失败：区分容器内（`kafka:29092`）与宿主（`localhost:9092`），可通过环境变量 `KAFKA_BOOTSTRAP_SERVERS` 覆盖。

> 建议 agent 阅读本文件后，结合 `README.md`（系统描述）与 `实验.md`（场景细节）即可快速定位需要的模块，并按照上面的路径进入具体代码。

