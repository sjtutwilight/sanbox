# 基于 k3s 的多实例实验演进方案

> 说明：这份文档描述的是平台演进路线，不等同于当前已经落地的全部资源。  
> 当前仓库已补齐 `k8s/base` 和 `k8s/overlays/k3s` 骨架，Redis Cluster 的样板安装步骤单独放在 `K3S_DEPLOYMENT_GUIDE.md`。

针对 “Redis 分片”“Kafka 宕机重启数据丢失” 与 “数据接入→Flink→数据应用” 的端到端压测需求，以下方案在现有 `docker-compose` 体系基础上，规划一套可在 k3s 中逐步落地的分阶段架构。

## 1. 目标与范围
1. **统一调度**：用 k3s 代替单机 Docker，对 Redis/MySQL/Kafka/Flink/Load Executor 等组件实现多实例、滚动升级、配额隔离。
2. **实验多场景化**：在同一集群里跑 Redis Cluster 分片、Kafka Broker 故障与实时数据通路压测，保证实验互不干扰。
3. **观测一致性**：沿用 Micrometer + Prometheus + Grafana + Loki 体系，只是改用 Helm/Operator 安装，保持 Dashboard/Log 查询体验不变。
4. **迭代友好**：保留 `docker-compose` 用于本地开发，k3s 作为“多实例实验”环境；二者共享镜像与配置模板。

## 2. 总体拓扑
| 层级 | 组件 | k3s 载体 | 说明 |
| --- | --- | --- | --- |
| 管控 | control-plane-app、frontend | `platform-apps` + `Deployment` | 通过 `Service` 暴露给集群内外；Ingress 交给内置 Traefik。 |
| 执行 | load-executor | `platform-apps` + `Deployment` | 统一从 overlay 注入 Redis/Kafka/Nacos 地址，不在业务代码内分叉环境逻辑。 |
| 数据层 | Redis Cluster、MySQL Primary/Replica、Kafka 集群、MinIO（可选） | `platform-storage` 中的 `StatefulSet`/Helm Chart | 使用 Longhorn/rook-ceph 提供 RWX/RWO PVC。 |
| 流处理 | Flink JM/TM、Flink Kubernetes Operator | `platform-streaming` | Operator 负责提交 Job/SessionCluster，JM/TM 作为 StatefulSet。 |
| 观测 | Prometheus Operator、Grafana、Loki/Promtail、Tempo（可选） | `platform-observability` | `ServiceMonitor` 抓取 load-executor/experiment pods 的 Micrometer 指标。 |

### 节点规划
- `master`（1 节点）：运行 k3s server、控制面组件、Prometheus Operator。
- `worker`（>=2 节点）：其中一台偏向存储 StatefulSet (Redis/Kafka/MySQL)，另一台跑 Flink/Load Executor。按需再扩容。
- 使用 `nodeSelector` 或 `topologySpreadConstraints` 把 StatefulSet 分布到不同节点，用于失效/容灾实验。

## 3. 组件部署建议

### 3.1 基础设施
1. **安装 k3s**：`curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik=false" sh -`；worker 使用 `k3s agent --server ...`.
2. **存储**：安装 Longhorn（优先使用 Helm）供 StatefulSet 挂载，Redis Cluster 样板依赖这一层。
3. **Helm 仓库**：`helm repo add bitnami https://charts.bitnami.com/bitnami`、`helm repo add grafana https://grafana.github.io/helm-charts` 等。

### 3.2 Redis 分片 / Sentinel 实验
目标：构建多主多从集群，可进行“分片扩容”“节点故障”“热点 key 打散”实验。

| 配置 | 建议 |
| --- | --- |
| Chart | `bitnami/redis-cluster`（原生 Cluster 模式）+ `bitnami/redis` Sentinel 模式（用于缓存方案对比）。 |
| 安装 | `helm install redis-cluster bitnami/redis-cluster --namespace platform-storage --set cluster.nodes=6 --set cluster.replicas=1 --set metrics.enabled=true --set persistence.storageClass=longhorn`。 |
| 观测 | Prometheus 自动抓取 `ServiceMonitor`；Grafana 复用 `redis-overview` 面板。 |
| 实验 | - **分片扩容**：`helm upgrade redis-cluster ... --set cluster.nodes=9`，观察 re-shard 时 load-executor 的 hit/miss。<br>- **节点杀戮**：`kubectl delete pod redis-cluster-0`，验证 `load-executor` 中 `favorite` 场景的失败率、fallback。 |

落地步骤：
1. 在 `platform-apps` 命名空间中创建 `Secret`，将 Redis 密码提供给 load-executor；k8s overlay 只注入连接参数，不在业务代码内写死基础设施地址。
2. 控制面 UI 扩展一个 k3s profile（`/api/experiments` 返回 `platform` 字段），用于切换 Redis 连接串。

### 3.3 Kafka 多 Broker + 宕机数据丢失验证
使用 3 Broker StatefulSet，配合 Strimzi Operator 或 bitnami Kafka chart。

| 项目 | 设计 |
| --- | --- |
| Chart | `strimzi/strimzi-kafka-operator` → `Kafka` CR（3 brokers + 3 ZK-less controllers）；或 `bitnami/kafka` + `KRaft=true`。 |
| 存储 | 每个 broker `PersistentVolumeClaim`（例如 20Gi），`retention.ms` 缩短以便观察数据丢失。 |
| Metrics | `kafka-exporter` 部署在同 namespace，通过 ServiceMonitor 暴露 9308。 |
| 测试流程 | 1) `kubectl scale kafka my-cluster-kafka --replicas=1` 模拟只剩单副本；2) load-executor 运行 `kafka_kline/produce_kline`；3) `kubectl delete pod my-cluster-kafka-0 --force --grace-period=0`；4) Broker 恢复后使用 Kafka UI/Flink sink 比对 offset，确认丢失/重复。 |

为配合丢失测试，可新增一个 `experiment.kafka.failure` operation，注入特定 `acks=1`、`unclean.leader.election.enable=true` 参数，方便对照。

### 3.4 Flink & 实时数据通路
1. 安装 **Flink Kubernetes Operator**：`helm install flink-operator flink-operator/flux -n streaming`（示例，具体 chart 依据官方文档）。
2. 定义 `FlinkDeployment` CR 将 `flink-jobmanager`/`flink-taskmanager` 运行在 `streaming` namespace，`parallelism`、`resource` 配置与 docker-compose 保持一致。
3. 数据流：`load-executor (favorite/kafka_kline)` → `Kafka topic` → `Flink Job` → `MySQL sink`（或 `Redis` / `MinIO`）。
4. 端到端压测：
   - 使用 `RunOrchestrator` 触发写入 Kafka 的 operation。
   - 提交 Flink Job（`kubectl apply -f flink-job.yaml`），其中包含实时计算逻辑。
   - `data-application`（可复用 `control-plane-app` 或新增 `query-service` Deployment）从 MySQL/Redis 拉数据，对外提供 API。
   - 在 Grafana 中关联 `experimentRunId`、`flinkJobId`、`topic` label，观察 QPS、lag、Flink Checkpoint 指标。

## 4. 部署流水线
1. **镜像构建**：复用当前 Maven/npm 构建脚本，输出 `load-executor`、`control-plane-app`、`frontend` 镜像。将镜像推送到 k3s 可访问的 registry（可选：部署 `registry.kube-system.svc.cluster.local:5000`，通过 `k3s ctr images import` 导入）。  
2. **配置管理**：在 `k8s/overlays/k3s/` 使用 `ConfigMapGenerator` + `Secret` 创建运行参数集合：
   - `ConfigMap`：统一注入 Redis/Kafka/Nacos/Grafana/Loki 地址。
   - `Secret`：数据库密码、Kafka SASL（如需要）。
3. **部署顺序**：
   1. `kubectl apply -k k8s/base/namespaces`（创建 `platform-ingress/platform-storage/platform-streaming/platform-apps/platform-observability`）。
   2. `kubectl apply -k k8s/overlays/k3s`（注入控制面与执行器运行参数，并挂载 Ingress）。
   3. `helm install` Redis/Kafka/MySQL/Nacos/Prometheus/Grafana/Loki。
   4. 视需要再补 `k8s/jobs/flink/`（Operator + JobDescriptor）。

## 5. 实验场景落地

### 5.1 Redis 分片压测
1. `kubectl port-forward svc/redis-cluster 6379:6379 -n data`（如需外部访问）。
2. 在控制面 UI 选择 `favorite/default/read_cache_aside`，dataRequest 中加入 `redisProfile="cluster"`，load-executor 将读取 `ConfigMap` 中的 cluster URI。
3. 运行 baseline（6 节点），记录 `load_executor_requests_total`、`redis_cluster_stats_masters`。
4. 扩容/缩容/杀 Pod，对比 `RunMetrics.maxLatency` / `favorite` 命中率。

### 5.2 Kafka 宕机与数据丢失
1. 运行 `kafka_kline/produce_kline`，写入 topic `binance.kline`.
2. 在 `strimzi` 中执行 `kubectl patch kafka my-cluster -p '{"spec":{"kafka":{"config":{"unclean.leader.election.enable":true}}}}' --type=merge`。
3. `kubectl delete pod my-cluster-kafka-0`；Kafka 重启后，使用 Flink job 或 `kafka-consumer-perf-test.sh` 对比 offset。
4. 在 Grafana (`Kafka Observability`) 中查看 `kafka_controller_kafkacontroller_offlinepartitionscount`、`kafka_server_replicamanager_underreplicatedpartitions` 指标。

### 5.3 实时通路端到端
1. `load-executor` 对 `wallet_query/publish_bus` 写 Kafka，或 `favorite/add_favorite` 对 MySQL/Redis 写压。
2. 提交 Flink job `wallet_snapshot_enrich`，消费 Kafka、写 MySQL `wallet_snapshot_summary`.
3. `data-application` Deployment 提供 `/wallet` API，供前端/脚本调用、验证实时性。
4. 通过 `ExperimentRunId` 标签在 Grafana 里关联：`load_executor` QPS → `Kafka lag` → `Flink checkpoint duration` → `MySQL Exporter` QPS → `data-application` HTTP latency。

## 6. 进一步迭代
1. **GitOps**：引入 ArgoCD/Flux 管理 `k8s/` 目录，确保实验拓扑可审计回滚。
2. **调度自动化**：利用 `Argo Workflows` 或 `CronWorkflow` 定义“压测编排”，控制不同 operation 的启动顺序与 runId 传递。
3. **多集群**：在不同 k3s 集群或 namespace 中复制同一套实验，以比较不同资源配额/硬件。
4. **容量仿真**：结合 `kube-burner`、`k6-operator` 等工具，为控制面/负载执行器提供更大规模的多 run 压测。
5. **可观测增强**：加上 Tempo/Jaeger 以追踪 load-executor → experiment → datasource 的调用链；配合 `ServiceMesh`（Linkerd）模拟网络故障。

---

> 基于以上设计，可以先落 `k8s/base` 和 `k8s/overlays/k3s` 作为平台骨架，再按场景单独装 Redis Cluster、Kafka、Flink 等 Helm Chart。这样平台边界和场景边界是分开的，后续扩展不会反向污染底座。
