## 1. 平台编排基线

- [ ] 1.1 新增 `k8s` 目录基线结构（namespaces、base、overlays），定义 `platform=k3s` 的最小可运行编排清单
- [ ] 1.2 完成 k3s/helm/存储依赖安装脚本或操作文档，明确本地仿真环境前置条件
- [ ] 1.3 增加平台 profile 配置模型（platform/scenario 枚举与校验规则），并在配置中心统一声明

## 2. 控制面 profile 编排能力

- [ ] 2.1 扩展实验目录聚合链路，为 operation 返回支持的 `platform`/`scenario` profile 元数据
- [ ] 2.2 扩展启动接口入参模型，要求显式传入 `platform`/`scenario` 并进行参数校验
- [ ] 2.3 调整运行状态查询返回结构，保留 run 关联的 profile 上下文

## 3. 执行器 profile 驱动接入能力

- [ ] 3.1 重构 Redis 数据源访问端口，支持 standalone/sentinel/cluster 策略选择
- [ ] 3.2 在命令接收与运行上下文中注入 `platform`/`scenario`，建立 profile 到数据源策略映射
- [ ] 3.3 保持 experiment group 业务调用面稳定，禁止在业务操作逻辑内分叉基础设施连接细节

## 4. Redis 分片样板场景验证

- [ ] 4.1 在 k3s 中部署 Redis Cluster 样板场景，并完成连接参数注入
- [ ] 4.2 以 `favorite/read_cache_aside` 建立 baseline 压测流程，记录命中率、错误率、延迟
- [ ] 4.3 执行扩容与故障注入（如杀 Pod）并输出对比结论，验证“平台能力与场景能力解耦”

## 5. 观测与文档同步

- [ ] 5.1 为指标与日志补齐 `platform`、`scenario`、`experimentRunId` 标签并完成看板变量对齐
- [ ] 5.2 同步更新项目文档（`ARCHITECTURE.md`、`K3S_PLAN.md`、`REALTIME_PLATFORM_K3S.md`、`README.md`）反映最新实现
- [ ] 5.3 同步更新相关 skill 文档中关于编排/实验流程的描述，避免规范与实现漂移

## 6. 验收与收口

- [ ] 6.1 执行端到端联调（控制面 -> 执行器 -> Redis 分片场景 -> 观测）并固化验证记录
- [ ] 6.2 运行 `openspec validate` 并修复所有校验问题
- [ ] 6.3 基于验证结果整理后续扩展场景清单（如 Kafka 故障）并确认不改动当前平台能力边界
