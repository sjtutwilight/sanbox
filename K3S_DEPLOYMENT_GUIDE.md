# k3s 部署与 Redis Cluster 样板指南

> 目标：先把最小可用的 k3s 基线跑通，再把 Redis Cluster 作为第一个样板场景挂进去。  
> 时间区间统一按左闭右开处理，例如 `2026-04-18 10:00` 到 `2026-04-18 11:00` 表示 `[10:00, 11:00)`.

## 1. 前置条件

1. 已安装 `k3s`，并保留默认 `traefik`，这样 ingress 资源可以直接复用。
2. 已安装 `helm`，用于安装 Longhorn 和 Redis Cluster。
3. 集群至少 1 个 server、1 个 worker；如果要做故障注入，建议 1 server + 2 worker。
4. 节点磁盘满足 Longhorn 写入要求，避免把压测结果和存储抖动混在一起。

## 2. 安装顺序

### 2.1 安装 k3s

```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik=false" sh -
```

如果要扩 worker，在 worker 节点执行：

```bash
sudo k3s agent --server https://<server-ip>:6443 --token <token>
```

### 2.2 安装 Helm

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

### 2.3 安装 Longhorn

Longhorn 负责给 Redis Cluster 这类 StatefulSet 提供稳定 PVC。推荐先创建独立命名空间：

```bash
kubectl create namespace longhorn-system
helm repo add longhorn https://charts.longhorn.io
helm repo update
helm install longhorn longhorn/longhorn \
  --namespace longhorn-system \
  --set defaultSettings.defaultReplicaCount=2
```

安装完成后确认默认 StorageClass：

```bash
kubectl get storageclass
```

## 3. 先应用 k8s 基线

```bash
kubectl apply -k k8s/base/namespaces
kubectl apply -k k8s/overlays/k3s
```

说明：

- `k8s/base/namespaces` 只负责命名空间边界。
- `k8s/overlays/k3s` 负责把运行时参数统一注入到控制面和执行器。
- Redis、Kafka、MySQL 等数据层仍建议通过 Helm 单独安装，避免把样板和平台底座耦死。

## 4. Redis Cluster 样板部署

### 4.1 安装 Helm Chart

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
kubectl create namespace platform-storage
helm install redis-cluster bitnami/redis-cluster \
  --namespace platform-storage \
  --set cluster.nodes=6 \
  --set cluster.replicas=1 \
  --set metrics.enabled=true \
  --set persistence.enabled=true \
  --set persistence.storageClass=longhorn \
  --set persistence.size=8Gi \
  --set auth.enabled=true \
  --set auth.password='redis-demo-pass'
```

### 4.2 校验部署状态

```bash
kubectl get pods -n platform-storage
kubectl get svc -n platform-storage
kubectl get pvc -n platform-storage
```

### 4.3 注入连接参数

把 Redis 连接参数写入 `platform-apps` 命名空间，供控制面和执行器统一读取：

```bash
kubectl create secret generic platform-redis-connection \
  -n platform-apps \
  --from-literal=REDIS_HOST=redis-cluster-master.platform-storage.svc.cluster.local \
  --from-literal=REDIS_PORT=6379 \
  --from-literal=REDIS_PASSWORD=redis-demo-pass
```

如果后续要把 k8s 配置下发给 Spring Boot，可以把该 Secret 作为 `envFrom` 挂到 `load-executor`，而不是在业务代码里硬编码连接串。

## 5. 验证建议

1. 用 `redis-cli -c` 连到 cluster 服务，确认 slot 分配正常。
2. `kubectl delete pod` 删除任一 master / replica，观察 pod 重新拉起和 slot 恢复。
3. 在 `platform-apps` 中启动控制面和执行器后，先跑一个读缓存类场景，再跑扩缩容。

## 6. 本地访问

如果不想先配 ingress，可以直接做端口转发：

```bash
kubectl port-forward -n platform-apps svc/control-plane-app 8083:8083
kubectl port-forward -n platform-apps svc/load-executor 18082:18082
```

这样可以先验证 API 可达，再切到 `Ingress`。
