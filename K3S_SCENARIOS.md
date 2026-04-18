# k3s 后续场景清单

> 说明：下面这些场景都建立在当前 `k3s` 基线之上，目标是按场景加 overlay 或 helm release，不改平台底座语义。  
> 排序按建议优先级从高到低。

## 1. Redis 相关

### 1.1 Redis Cluster 扩缩容

- 目标：验证 shard 扩容时的命中率、延迟和 slot 迁移影响。
- 依赖：Longhorn、bitnami/redis-cluster。
- 平台边界：不变。

### 1.2 Redis 节点故障

- 目标：验证 `kill pod` 后的自动恢复、主从切换和短暂错误率。
- 依赖：Redis Cluster、观测看板。
- 平台边界：不变。

## 2. Kafka 相关

### 2.1 Broker 宕机重启

- 目标：验证消息丢失、重复消费与下游抖动。
- 依赖：Strimzi 或 bitnami Kafka Helm chart。
- 平台边界：不变。

### 2.2 Topic 压力上限

- 目标：验证 broker/partition 在高并发写入下的吞吐瓶颈。
- 依赖：Kafka + exporter。
- 平台边界：不变。

## 3. Flink 相关

### 3.1 JobManager 重启

- 目标：验证 checkpoint 恢复时间和下游写入连续性。
- 依赖：Flink Kubernetes Operator。
- 平台边界：不变。

### 3.2 TaskManager 横向扩容

- 目标：验证并发提升后吞吐和背压变化。
- 依赖：Flink Operator、HPA 或手动扩容。
- 平台边界：不变。

### 3.3 状态后端切换

- 目标：比较 RocksDB 与后续状态后端方案的 checkpoint/恢复差异。
- 依赖：Flink job 配置覆盖。
- 平台边界：不变。

## 4. 控制面与执行器

### 4.1 load-executor 横向扩容

- 目标：验证多副本下 run 调度与标签一致性。
- 依赖：控制面 profile 支持、幂等 run 标识。
- 平台边界：不变。

### 4.2 控制面断连恢复

- 目标：验证控制面短暂不可用时，已启动 run 的状态查询和恢复表现。
- 依赖：Ingress、Service、load-executor。
- 平台边界：不变。

## 5. 观测与平台

### 5.1 Prometheus 抓取压力

- 目标：验证指标暴增时的抓取稳定性和 dashboard 响应。
- 依赖：Prometheus Operator、Grafana。
- 平台边界：不变。

### 5.2 Loki 日志洪峰

- 目标：验证日志量突增时的查询延迟和存储占用。
- 依赖：Loki + Promtail。
- 平台边界：不变。

## 6. 不建议立即纳入的平台级变更

1. 新增第二套 k8s 编排底座。
2. 把场景和平台边界重新拆成多个命名空间体系。
3. 把 Redis 之外的中间件优先写进平台基线。

这些内容会把当前基线拉散，建议等当前 Redis 样板跑稳后再开新 change。
