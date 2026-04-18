# 实时数据平台 k3s 编排设计（单场景聚焦版）

## 1. 设计目标
1. 将仓库聚焦到“实时数据平台”一个场景：`load-executor` 产生流量 → Kafka 数据接入 → Flink 计算 → Redis/HBase/MySQL/Elastic 等数据应用。
2. 以 k3s 为唯一编排层，满足真实生产级要求：多 TaskManager/Worker、Kafka 多 Broker、Flink 状态可在 RocksDB 与 HBase 之间切换。
3. 统一本地环境：`docker-compose` 仅保留一份，借助 `profiles` 区分功能组，便于在 k3s 上按场景启停镜像组合。

## 2. k3s 集群拓扑
| Namespace | 组件 | 说明 |
| --- | --- | --- |
| `platform-ingress` | Traefik / Ingress-Nginx、Cert-Manager | 提供外部访问控制面、Grafana、Kafka UI。 |
| `platform-storage` | MySQL Operator、Redis Cluster、HBase (HDFS+HBase Master/RegionServer)、MinIO | 存储层，StatefulSet + Longhorn/CSI 卷。 |
| `platform-streaming` | Flink Operator、Flink JM/TM、Kafka 集群、Schema Registry、Kafka Connect | 实时处理主路径。 |
| `platform-apps` | load-executor、control-plane-app、frontend、data-application API | 实验流量与数据消费端。 |
| `platform-observability` | kube-prometheus-stack、Loki+Promtail、Tempo（可选）、Alertmanager | 统一观测。 |

### 节点规划
- `master`：运行 k3s server、control-plane-app、Prometheus Operator。
- `worker-stream`（>=2）：Flink TaskManager、load-executor、Kafka Broker，保证多 worker 实验。
- `worker-storage`：HBase/Redis/MySQL。
利用 `nodeSelector/affinity` 固定 StatefulSet，便于做故障/重启实验。

## 3. 流程与组件
1. **数据接入**：`load-executor` → Kafka（`platform-streaming` namespace）。支持多 topic（行情、钱包、埋点）。Kafka 通过 Strimzi Operator 部署 3 Broker + 3 Controller，PVC 20 GiB。
2. **计算层**：Flink Operator 管理 `FlinkDeployment`。每个 job：
   - JobManager `1`、TaskManager `N`（HPA 根据 `flink_taskmanager_job_task_queue_size` 调整）。
   - 状态后端可动态切换（见 §4）。
3. **存储层**：
   - Redis Cluster（6 shard + 1 replica）用于热点 cache。
   - MySQL 主从（via Oracle MySQL Operator）用于交易/钱包/结果表。
   - HBase（HDFS NameNode + DataNode + HBase Master/RegionServer）承载大状态/历史指标。
4. **数据应用**：`data-application` Deployment 暴露 REST/gRPC 接口读取 Redis/MySQL/HBase。
5. **观测**：Prometheus Operator 自动抓取：Flink、Kafka、Redis、load-executor、apps；Grafana 复用仓库 dashboard；Loki 收集所有容器日志。

## 4. Flink 状态后端切换方案
1. **配置管理**：在 `platform-streaming` 中定义 `ConfigMap flink-backend-config`，包含：
   ```yaml
   state.backend.type: rocksdb
   state.backend.incremental: true
   state.checkpoints.dir: s3://flink-checkpoints/...
   state.savepoints.dir: s3://flink-savepoints/...
   state.backend.hbase.zookeeper.quorum: hbase-zk:2181
   ```
2. **FlinkDeployment CR**：将 `spec.job.stateBackend` 设置为 `{{ .Values.flinkStateBackend }}`。通过 `kubectl patch flinkdeployment wallet-job --type=merge -p '{"spec":{"job":{"stateBackend":"rocksdb"}}}'`/`"hbase"` 切换。
3. **HBase 状态**：基于社区扩展（如 Ververica HBase State Backend）或自定义 RocksDB Snapshot offload to HBase。配置 `state.backend.type: hbase` 时，Flink Operator 注入 HBase 依赖 JAR。
4. **实验步骤**：
   - baseline：`RocksDB` + 4 TM × 8 slot，记录 checkpoint size、latency。
   - 切换 `stateBackend=hbase` + 启动 RegionServer 水平扩容，观察 checkpoint duration、外部存储延迟。
   - 对比 `ExperimentRunId` 维度 metrics -> Grafana。

## 5. 多 Worker 及弹性策略
- Flink TaskManager `Deployment` 使用 `HPA`（CPU 60%、lag 指标），最少 2，最多 8，保障 Kafka 分区与 Task 并行度。
- `load-executor` `Deployment` 允许多副本，利用 `experimentRunId` 分片 run。
- Kafka Broker StatefulSet 3 副本 + PodDisruptionBudget，便于 Broker 宕机测试。

## 6. docker-compose 统一策略
虽然最终运行在 k3s，但仍保留 `docker-compose.yml` 作为本地集成测试工具。改造建议：

1. **拆分 profiles**：
   ```yaml
   services:
     redis:
       profiles: ["storage","all"]
     mysql:
       profiles: ["storage","all"]
     kafka:
       profiles: ["streaming","all"]
     flink-jobmanager:
       profiles: ["streaming","all"]
     load-executor:
       profiles: ["apps","all"]
     control-plane:
       profiles: ["apps","all"]
     observability-stack:
       profiles: ["observability","all"]
   ```
   启动：`docker compose --profile storage --profile streaming up -d`.

2. **复用镜像**：Compose 构建的镜像推送到本地 registry (`k3s ctr images import`)，k3s 直接使用，避免重复定义。

3. **环境变量对齐**：`application-kubernetes.yml` 与 compose `.env` 保持一致。所有服务通过 `CONFIG_PROFILE={docker,k3s}` 切换连接串。

4. **自定义组合**：在 control-plane 中新增 “启动/停止资源” API，调用 GitOps（Flux/ArgoCD）或 `kubectl scale` 以按需启停 Helm release，而不是一次性启动全部组件。

## 7. 启停流程
1. **开发/小规模实验**：`docker compose --profile apps --profile streaming up load-executor control-plane kafka`。
2. **k3s 集群**：
   - `kubectl apply -k k8s/base/namespaces`
   - `kubectl apply -k k8s/overlays/k3s`
   - `helm install longhorn ...`，再安装 Redis Cluster、Kafka、Flink 等 Helm Chart
   - 按场景启停 `k8s` 资源时，优先使用命名空间和 overlay，而不是回头改业务代码
   - 使用 `ArgoCD`/`Flux` 管理 HelmRelease，按场景启停（例如只开 streaming + apps）

## 8. 观测与压测实践
- Grafana Dashboard 统一加 `var-environment={docker,k3s}`、`var-stateBackend={rocksdb,hbase}`，可以快速对比。
- 在 `load_executor_requests_total`、`flink_jobmanager_jobs_uptimes`、`hbase_regionserver_compactionQueueSize` 等指标上标记 runId。
- Kafka 宕机/重启流程：`kubectl delete pod kafka-kafka-0`，观察 Flink Checkpoint failover + 下游 Redis/MySQL 波动。
- 大状态实验：使用 `load-executor` 的 wallet/wallet_query operation 生成海量状态，切换 state backend 并记录 RocksDB SST 大小 vs HBase RegionStore IO。

## 9. 后续计划
1. **GitOps 化**：`k8s/overlays/{docker,k3s}` 目录 + ArgoCD 应用。
2. **一键场景脚本**：`make profile=k3s scenario=streaming up`，脚本根据 `scenario` 选择 Helm release。
3. **状态回收**：集成 HBase compaction / RocksDB TTL 清理 job，防止实验数据无限增长。
4. **性能回放**：在 `control-plane` 新增“Scenario Template”，可一次性触发 load-executor + Flink state 切换 + Kafka 故障，用于回放生产事故。

通过以上方案，可以在 k3s 上部署一套贴近生产的实时数据平台：多 worker Flink、可切换状态后端、Kafka/HBase/Redis/MySQL 完整链路，并且通过 Compose profiles + GitOps 实现“按场景启停”的运行方式，而非所有镜像同时启动。这样既满足大数据量实验的真实性，也简化了环境统一与日常维护。

## 10. 与当前仓库实现对齐

当前仓库已经补了一个更小的 k3s 基线：

- `k8s/base/namespaces` 负责平台命名空间
- `k8s/base/apps/*` 负责控制面、执行器、前端的最小部署壳
- `k8s/overlays/k3s` 负责统一运行参数和 ingress

Redis Cluster 的样板安装、验证模板和后续场景清单分别放在：

- `K3S_DEPLOYMENT_GUIDE.md`
- `K3S_VERIFICATION_TEMPLATE.md`
- `K3S_SCENARIOS.md`

这份文档里的 `platform-storage/platform-streaming/platform-apps` 分层仍然成立，但当前代码先落的是基础边界和 Redis 样板，不把所有中间件一次性塞进平台骨架。
