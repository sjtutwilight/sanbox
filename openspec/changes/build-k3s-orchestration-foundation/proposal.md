## Why

当前项目已有 Docker Compose 级别的本地实验环境，但缺少面向多实例、故障注入和资源隔离的统一编排能力，导致“接近生产”的实验无法稳定复现。现在需要先建设 k3s 编排底座，并用一个低风险场景快速验证编排链路可用性。

## What Changes

- 新增面向实验平台的 k3s 编排能力定义，包括命名空间分层、组件部署边界、配置注入与观测接入约束。
- 新增“场景可插拔”的验证机制：先以 Redis 分片作为第一步样板场景验证编排能力，但不将 Redis 作为编排体系的唯一依赖。
- 调整控制面实验目录与运行编排契约，支持声明运行平台/场景 profile（如 docker/k3s、redis-sharding/kafka-failure）。
- 调整负载执行层能力契约，支持按 profile 选择数据源连接策略（standalone/sentinel/cluster）并暴露一致的运行指标。
- **BREAKING**：实验编排语义从“默认单环境”升级为“显式平台 profile 驱动”，未提供 profile 的旧调用将不再作为长期默认路径。

## Capabilities

### New Capabilities
- `platform-orchestration`: 定义 k3s 编排底座能力、场景模板与可观测接入标准，明确平台能力与具体中间件场景解耦。

### Modified Capabilities
- `experiment-orchestration`: 扩展实验目录与启动接口语义，增加平台/profile 维度与场景编排上下文。
- `load-execution`: 扩展执行器在不同 profile 下的数据源接入与指标标注能力，支持 cluster/sentinel/standalone 策略切换。

## Impact

- 受影响代码：`control-plane-app` 的实验目录与启动编排链路，`load-executor` 的 Redis 接入抽象与参数映射，前端实验启动参数模型。
- 受影响 API：`/api/experiments` 元数据与启动请求参数结构，`/commands` 入参与运行标签。
- 基础设施依赖：新增 k3s/helm/存储插件（如 Longhorn）作为编排基础依赖。
- 文档与规范：需要新增平台编排 capability spec，并同步更新现有 `experiment-orchestration`、`load-execution` spec 的行为定义。
