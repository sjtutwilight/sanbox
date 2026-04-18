## Context

当前系统通过 `docker-compose` 提供单机实验，适合快速开发验证，但在多实例调度、资源隔离、故障注入和滚动变更方面能力不足。仓库已有两份 k3s 方案文档（`K3S_PLAN.md`、`REALTIME_PLATFORM_K3S.md`），说明方向清晰，但代码与规格层仍缺少统一契约：

- 控制面实验目录未显式表达平台与场景 profile。
- 执行器数据源接入默认单节点 Redis 语义，尚未形成 standalone/sentinel/cluster 统一能力面。
- 观测虽然完备（Prometheus/Grafana/Loki），但缺少“按平台/场景/run 维度一致打标”的约束。

利益相关方包括：实验平台开发者（需要稳定演进路径）、实验执行者（需要一致操作体验）、性能分析者（需要可比较的数据）。

## Goals / Non-Goals

**Goals:**
- 建立与中间件类型解耦的编排能力基线，以 k3s 作为编排目标平台。
- 定义“平台能力”与“验证场景能力”的边界，Redis 分片仅作为首个样板场景。
- 统一控制面与执行器的 profile 语义，支持按 platform/scenario 选择运行策略。
- 为后续 Kafka 故障、Flink 拓扑等场景提供可复用接入面，而非复制专用逻辑。

**Non-Goals:**
- 本次不追求一次覆盖全部中间件场景（Kafka/Flink/HBase 等仅定义接入形态）。
- 本次不要求向旧的“无 profile 请求”保持兼容。
- 本次不设计生产级多集群治理（仅单集群能力建设）。

## Decisions

### 决策 1：采用“双层能力模型”而不是“按中间件建编排能力”
- 选择：
  - 层 1 `platform-orchestration`：定义命名空间、部署边界、配置注入、观测接入、故障注入入口。
  - 层 2 场景 profile：如 `redis-sharding`、`kafka-broker-failure`，通过统一 profile 协议挂接。
- 原因：避免把 k3s 能力绑定到 Redis，实现“先样板、后扩展”。
- 备选方案：直接将 Redis Cluster 作为编排核心模型。
- 放弃原因：会导致后续 Kafka/Flink 场景复用困难，平台能力演进被具体中间件绑架。

### 决策 2：控制面接口显式携带 platform/scenario profile
- 选择：实验目录返回可用 profile 元数据；启动请求显式声明 profile。
- 原因：运行语义可观察、可审计、可重放，减少“默认环境”歧义。
- 备选方案：后端隐式按环境变量推断 profile。
- 放弃原因：无法在同一集群多场景并行实验时保持确定性。

### 决策 3：执行器采用“策略化数据源接入端口”
- 选择：执行器能力契约要求按 profile 切换连接策略（standalone/sentinel/cluster），并统一错误模型与指标标签。
- 原因：把运行策略变化隔离在接入层，保持实验组调用面稳定。
- 备选方案：在各 experiment group 中分支处理不同 Redis 模式。
- 放弃原因：业务实验逻辑会被基础设施细节污染，维护成本高。

### 决策 4：观测标签统一包含 platform/scenario/run
- 选择：关键指标和日志必须具备 `platform`、`scenario`、`experimentRunId` 维度。
- 原因：保证不同平台与场景实验可横向对比。
- 备选方案：只保留 runId。
- 放弃原因：无法稳定区分平台差异与场景差异。

## Risks / Trade-offs

- [Risk] profile 语义升级会导致调用方短期不适应  
  → Mitigation：在文档与任务中提供明确迁移路径，并在控制面接口校验缺失 profile 时给出可读错误。

- [Risk] k3s 基础设施准备（helm/存储/网络）增加环境复杂度  
  → Mitigation：分阶段落地，先最小闭环（k3s + redis-sharding 场景 + 观测）再扩面。

- [Risk] 引入策略化数据源后，运行时配置矩阵复杂度上升  
  → Mitigation：限定 profile 命名规范并在 spec 中要求 profile 可枚举。

- [Risk] 因不做向前兼容，旧链路可能直接失效  
  → Mitigation：明确 BREAKING 范围，按“控制面 → 执行器 → 前端”顺序一次性切换。

## Migration Plan

1. 先完成规格：新增 `platform-orchestration`，并修改 `experiment-orchestration`、`load-execution`。
2. 控制面先落地 profile 契约（目录 + 启动请求），保证编排入口稳定。
3. 执行器切换到策略化数据源接入端口，并落地 Redis 分片样板场景。
4. 观测补齐 profile 标签后，执行基线/扩容/故障注入三类验证。
5. 通过后再扩展第二个场景（如 Kafka 故障），验证“平台与场景解耦”成立。

回滚策略：若 profile 化入口出现严重问题，整体回退到上一个变更版本（不做局部兼容分支），避免长期双语义并存。

## Open Questions

- platform 与 scenario 的命名边界是否需要在控制面持久化，还是仅作为运行时参数？
- Redis 分片样板场景中，是否要求同时纳入 sentinel 作为对照路径？
- profile 标签是否需要进入日志索引字段，还是仅进入 metrics label？
