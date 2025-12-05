## Observability Toolkit for Java Experiments

`load-executor` 已经接入 Micrometer / Prometheus / Loki，配合本目录提供的脚本，可以快速对 Java 实验进行性能与资源观测：

| 维度 | 工具 | 说明 |
|------|------|------|
| Metrics | Micrometer -> Prometheus -> Grafana | JVM、线程池、业务自定义指标，Grafana 已预置面板 |
| Logs | Promtail -> Loki -> Grafana | `logs/app/*.log` 会被收集，便于查询实验 run |
| Profiling | Java Flight Recorder、async-profiler | 现场采集 CPU/内存/锁等热点 |

> 约定：所有脚本均在仓库根目录执行（`/Users/.../sandbox`）。

---

### 1. Prometheus + Grafana + JVM Dashboard

1. 启动基础观测组件：

```bash
docker-compose up -d prometheus grafana loki promtail redis-exporter mysqld-exporter
```

2. `load-executor` 默认暴露 Micrometer 指标（`/actuator/prometheus`）。Prometheus 抓取后可直接在 Grafana 打开以下看板：
   - `grafana/provisioning/dashboards/jvm-micrometer-dashboard.json`：JVM heap/GC/线程/类加载指标。
   - `grafana/provisioning/dashboards/logs-dashboard.json`：结合 Loki 查看实验日志。
   - `grafana/provisioning/dashboards/redis-*` / `mysql-*`：观测依赖组件状态。

3. Grafana 入口：<http://localhost:3000>（默认 admin/admin）。仪表盘已经在 provisioning 中自动导入，无需手动上传。

> 当添加新的 Micrometer 指标或标签时，可以直接复制 `grafana/provisioning/dashboards/jvm-micrometer-dashboard.json` 做自定义版本。

---

### 2. Java Flight Recorder (JFR)

JDK 17 自带 Flight Recorder，可在不停机情况下抓取 CPU、对象分配、锁争用等细节。脚本位于 `observability/jfr/`：

- `start-recording.sh <pid>`：启动一次录制。
- `stop-recording.sh <pid>`：提前停止并导出。

示例：

```bash
# 1) 找到 load-executor 的 PID
jps -l | grep load-executor

# 2) 开始一个 120 秒的 profile，采样配置使用官方 profile 模板
./observability/jfr/start-recording.sh <pid> 120

# 3) 如果需要提前结束
./observability/jfr/stop-recording.sh <pid>
```

脚本默认把 `.jfr` 文件写入 `observability/jfr/recordings/`，可用 Java Mission Control 打开。若要调整录制模板或输出路径，可通过环境变量覆盖：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JFR_DURATION_SEC` | 120 | 录制时长，秒 |
| `JFR_SETTINGS` | `profile` | JFR 模板，可选 `default`, `profile`, `continuous` |
| `JFR_OUTPUT_DIR` | `observability/jfr/recordings` | 输出目录 |

---

### 3. async-profiler

`async-profiler` 适合采集火焰图（CPU、alloc、lock、wall）。我们提供两个脚本：

1. `observability/async-profiler/download.sh`  
   - **自动检测操作系统**（macOS / Linux），下载对应版本（默认 v4.2.1）。  
   - 解压到 `observability/async-profiler/bin/async-profiler-<ver>-<platform>/`。

2. `observability/async-profiler/profile.sh <pid>`  
   - 自动查找 `bin/*/bin/asprof`（新版）或 `profiler.sh`（旧版），执行完生成 HTML 火焰图，文件位于 `observability/async-profiler/profiles/`。

示例：

```bash
# 1) 下载工具（只需一次，自动检测 macOS/Linux）
./observability/async-profiler/download.sh

# 2) 对 load-executor 进行 60s CPU 采样
./observability/async-profiler/profile.sh <pid>

# 3) 如果要抓对象分配或锁，修改 EVENT
EVENT=alloc ./observability/async-profiler/profile.sh <pid>
EVENT=lock ./observability/async-profiler/profile.sh <pid>

# 4) 自定义采样时长
DURATION=30 ./observability/async-profiler/profile.sh <pid>
```

关键环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `ASYNC_PROFILER_VERSION` | `4.2.1` | 下载版本 |
| `ASYNC_PROFILER_URL` | 根据版本和 OS 推导 | 可显式指定 release 地址 |
| `ASYNC_PROFILER_DIR` | `observability/async-profiler/bin` | profiler 所在目录 |
| `EVENT` | `cpu` | 事件类型：`cpu`, `alloc`, `lock`, `wall` 等 |
| `DURATION` | `60` | 采样秒数 |
| `OUTPUT_DIR` | `observability/async-profiler/profiles` | 火焰图输出目录 |

生成的 HTML 文件可直接用浏览器打开（交互式火焰图）。对于远程服务器，可把文件拷出或上传到 Grafana (FlameGraph Panel) 进行对比。

---

### 4. 与 load-executor 的结合建议

1. **标注实验信息**：在 `Command.dataRequest` 中加入 `experimentRunId` / `tags`，Grafana dashboard 上即可通过 label 过滤。
2. **控制变量**：使用之前引入的 Nacos 动态参数（见 `实验.md`）快速尝试不同 TTL/Topic，再结合 metrics + profiler 分析差异。
3. **排查流程**：推荐顺序
   1. Grafana JVM 面板看线程、GC、系统负载；
   2. 如出现 CPU/锁瓶颈，用 `async-profiler` 定位热点；
   3. 需要更细粒度事件时，启动 JFR 做深度分析。

通过以上工具，可以在本地即可复现完整的“指标看板 + flame graph + JFR”链路，为后续 Java 实验提供可观测保障。
