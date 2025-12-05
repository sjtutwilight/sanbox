# 快速启动指南

## 一、前置要求

- JDK 17+
- Maven 3.x
- Docker & Docker Compose
- 至少4GB可用内存

## 二、快速启动(推荐)

### 1. 一键启动所有服务

```bash
./start.sh
```

该脚本会自动:
1. 启动Docker服务(Redis, Redis Exporter, Prometheus, Grafana)
2. 编译Spring Boot项目
3. 启动应用

### 2. 访问服务

启动完成后,可以访问以下地址:

| 服务 | 地址 | 说明 |
|-----|------|------|
| Spring Boot应用 | http://localhost:8080 | 应用主页 |
| Actuator健康检查 | http://localhost:8080/actuator/health | 健康检查端点 |
| Prometheus指标 | http://localhost:8080/actuator/prometheus | 应用指标端点 |
| Prometheus UI | http://localhost:9090 | Prometheus Web界面 |
| Grafana | http://localhost:3000 | Grafana仪表盘 (admin/admin) |
| Redis Exporter | http://localhost:9121/metrics | Redis指标端点 |

## 三、手动启动(分步骤)

### 步骤1: 启动Docker服务

```bash
docker-compose up -d
```

等待所有容器启动完成:
```bash
docker-compose ps
```

### 步骤2: 编译项目

```bash
mvn clean package -DskipTests
```

### 步骤3: 启动Spring Boot应用

```bash
java -jar target/scheduler-redis-monitor-1.0.0-SNAPSHOT.jar
```

或使用Maven:
```bash
mvn spring-boot:run
```

## 四、验证系统运行

### 方法1: 使用验证脚本

```bash
./verify.sh
```

### 方法2: 手动验证

#### 1. 检查Spring Boot应用

```bash
curl http://localhost:8080/actuator/health
```

预期输出:
```json
{"status":"UP"}
```

#### 2. 检查Prometheus指标

```bash
curl http://localhost:8080/actuator/prometheus | grep scheduler
```

应该看到类似输出:
```
scheduler_data_write_total 100.0
scheduler_data_write_success_total 100.0
scheduler_redis_keys_total 50.0
```

#### 3. 检查Redis数据

```bash
docker exec scheduler-redis redis-cli DBSIZE
```

应该看到数据数量在增长。

#### 4. 检查Redis Exporter

```bash
curl http://localhost:9121/metrics | grep redis_up
```

应该看到:
```
redis_up 1
```

## 五、查看Grafana仪表盘

### 1. 登录Grafana

访问 http://localhost:3000
- 用户名: `admin`
- 密码: `admin`

首次登录会提示修改密码,可以跳过或修改。

### 2. 查看仪表盘

系统自动配置了两个仪表盘:

#### Spring Boot Scheduler Monitor
- 路径: Dashboards → Spring Boot Scheduler Monitor
- 内容:
  - 数据写入总次数
  - 数据写入速率(每分钟)
  - 平均写入耗时
  - Redis Key总数
  - CPU使用率
  - JVM内存使用
  - JVM线程数
  - JVM GC次数
  - 数据值统计

#### Redis Dashboard for Prometheus Redis Exporter
- 路径: Dashboards → Redis Dashboard for Prometheus Redis Exporter 1.x
- 内容:
  - Redis运行时间
  - 连接客户端数
  - 内存使用率
  - 命令执行速率
  - 缓存命中/未命中
  - Key数量统计
  - 过期/驱逐Key统计
  - 网络I/O
  - 等等...

## 六、监控指标说明

### 业务指标(Spring Boot应用)

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| scheduler_data_write_total | Counter | 数据写入总次数 |
| scheduler_data_write_success_total | Counter | 数据写入成功次数 |
| scheduler_data_write_failure_total | Counter | 数据写入失败次数 |
| scheduler_data_write_duration_seconds | Timer | 数据写入耗时(秒) |
| scheduler_redis_keys_total | Gauge | Redis中的Key总数 |
| scheduler_last_write_timestamp_seconds | Gauge | 最后写入时间戳 |
| scheduler_data_value_distribution | Summary | 数据值分布统计 |
| redis_connection_status | Gauge | Redis连接状态(1=正常,0=异常) |

### Redis指标(通过redis_exporter)

redis_exporter提供了100+个Redis指标,包括:
- `redis_up`: Redis是否运行
- `redis_connected_clients`: 连接的客户端数
- `redis_memory_used_bytes`: 内存使用量
- `redis_memory_max_bytes`: 最大内存限制
- `redis_keyspace_hits_total`: 缓存命中次数
- `redis_keyspace_misses_total`: 缓存未命中次数
- `redis_db_keys`: 各数据库的Key数量
- `redis_commands_total`: 命令执行次数
- 更多指标请访问: http://localhost:9121/metrics

## 七、配置调整

### 修改定时任务频率

编辑 `src/main/resources/application.yml`:

```yaml
scheduler:
  fixed-rate: 10000  # 改为10秒执行一次
  data:
    batch-size: 20   # 每次写入20条数据
```

重新编译并启动应用。

### 修改Redis配置

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 5000ms
```

### 修改Prometheus抓取间隔

编辑 `prometheus/prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'spring-boot-app'
    scrape_interval: 5s  # 改为5秒抓取一次
```

重启Prometheus:
```bash
docker-compose restart prometheus
```

## 八、停止服务

### 停止Spring Boot应用

在运行应用的终端按 `Ctrl+C`

### 停止Docker服务

```bash
./stop.sh
```

或手动停止:
```bash
docker-compose down
```

### 清除所有数据(包括持久化数据)

```bash
docker-compose down -v
```

**注意**: 这会删除所有Redis数据、Prometheus历史数据和Grafana配置!

## 九、常见问题

### 1. 端口冲突

如果端口被占用,修改 `docker-compose.yml` 中的端口映射:

```yaml
ports:
  - "16379:6379"  # 将Redis端口改为16379
```

同时需要修改 `application.yml` 中的Redis端口。

### 2. Prometheus无法抓取应用指标

**症状**: Prometheus UI中显示目标为DOWN状态

**解决方案**:
1. 确认Spring Boot应用正在运行
2. 访问 http://localhost:8080/actuator/prometheus 确认指标端点可访问
3. 检查防火墙设置
4. 在macOS上,确认 `host.docker.internal` 可以解析

### 3. Grafana仪表盘没有数据

**症状**: Grafana仪表盘显示"No Data"

**解决方案**:
1. 检查Prometheus是否成功抓取指标
2. 在Prometheus UI (http://localhost:9090) 中查询指标是否存在
3. 检查Grafana数据源配置是否正确
4. 调整时间范围(右上角)
5. 等待几分钟让数据积累

### 4. Redis Exporter无法连接Redis

**症状**: redis_exporter日志显示连接错误

**解决方案**:
```bash
# 检查Redis是否运行
docker-compose ps redis

# 查看redis-exporter日志
docker-compose logs redis-exporter

# 重启redis-exporter
docker-compose restart redis-exporter
```

### 5. 内存不足

**症状**: Docker容器频繁重启或OOM

**解决方案**:
在 `docker-compose.yml` 中添加资源限制:

```yaml
services:
  redis:
    mem_limit: 512m
  prometheus:
    mem_limit: 1g
  grafana:
    mem_limit: 512m
```

## 十、开发调试

### 查看应用日志

应用日志会输出定时任务执行信息:
```
2024-11-23 10:00:00 [scheduling-1] INFO  c.e.s.task.DataWriteTask - 开始执行定时任务: 写入数据到Redis
2024-11-23 10:00:00 [scheduling-1] INFO  c.e.s.service.RedisDataService - 批量写入数据完成: 总数=10, 成功=10
2024-11-23 10:00:00 [scheduling-1] INFO  c.e.s.task.DataWriteTask - 定时任务执行完成: 本次写入=10, 成功=10, Redis总数=150, 耗时=25ms
```

### 查看Redis数据

```bash
# 进入Redis容器
docker exec -it scheduler-redis redis-cli

# 查看所有Key
KEYS scheduler:data:*

# 查看Key数量
DBSIZE

# 查看某个Key的值
GET scheduler:data:{某个UUID}

# 查看Key的TTL
TTL scheduler:data:{某个UUID}

# 清空数据库(谨慎!)
FLUSHDB
```

### 查看Docker容器状态

```bash
# 查看所有容器
docker-compose ps

# 查看容器日志
docker-compose logs -f redis
docker-compose logs -f prometheus
docker-compose logs -f grafana
docker-compose logs -f redis-exporter

# 查看容器资源使用
docker stats
```

## 十一、生产环境建议

在生产环境部署前,请考虑以下建议:

1. **安全加固**
   - Redis设置密码认证
   - Grafana修改默认密码
   - 使用HTTPS访问
   - 配置防火墙规则

2. **高可用**
   - Redis Sentinel或Cluster
   - 应用多实例部署
   - Prometheus联邦集群
   - Grafana高可用配置

3. **监控告警**
   - 配置Alertmanager
   - 设置告警规则
   - 集成钉钉/企业微信/邮件通知

4. **资源限制**
   - Docker容器资源限制
   - JVM堆内存配置(-Xmx, -Xms)
   - Redis最大内存设置(maxmemory)

5. **日志管理**
   - 集成ELK Stack或Loki
   - 日志分级和轮转
   - 敏感信息脱敏

6. **备份策略**
   - Redis RDB/AOF备份
   - Prometheus数据备份
   - Grafana配置备份

## 十二、技术支持

如有问题,请查看:
- 项目README: `README.md`
- 设计文档: `.note/设计文档.md`
- 提交Issue到项目仓库

---

**祝使用愉快!** 🎉
