# 1. 顶层框架：先把问题归类到哪种 “JVM 级” 故障

从 **JVM & 运行时** 视角看线上问题，大部分都可以归到 5 条线：

1. **进程级：活着吗？为啥挂？**
    - CrashLoop / Pod 重启 / 进程直接没了
    - `OutOfMemoryError`、`StackOverflowError`、SIGKILL 等
2. **内存 & GC：占多大？GC 在干嘛？**
    - 老年代一直顶着 90%
    - GC 非常频繁或暂停时间很长
    - Heap OOM（不是 Metaspace / 本地内存）
3. **CPU & 热点代码：在算啥？算得值不值？**
    - CPU 打满、接口变慢
    - GC 正常、IO 正常，但就是很耗 CPU
4. **线程 & 锁：线程都在干嘛？卡在哪？**
    - 线程数无故增长
    - 大量 BLOCKED / WAITING
    - 死锁、线程池耗尽、任务不再执行
5. **类加载 / Metaspace / JIT / JMM 这类“少见怪问题”**
    - `OutOfMemoryError: Metaspace`
    - 热部署 / 动态生成类后内存越跑越大
    - JIT 热身后性能反而抖动、偶发指令重排/JMM 相关问题

**决策习惯：**

> 入口只做一件事：先用指标和日志把问题放进上面 5 条线之一，然后走对应分支的排查路径。
> 
> 
> 不要一上来就 `jmap` / `jstack`，容易盲人摸象。
> 

---

# 2. 入口步骤：用指标和日志做第一次定位

## 2.1 先看 SLO & 资源指标

在 Prometheus / JFR / 监控里检查：

1. **可用性 & 性能：**
    - error rate、QPS、P95/P99 latency
2. **系统资源：**
    - 进程 CPU、机器 CPU
    - JVM Heap 使用、Metaspace 使用
    - GC 次数 / GC 时间（年轻代、老年代）
3. **线程：**
    - 线程总数、重要线程池的活跃线程数 / 队列长度
4. **重启情况：**
    - Pod restart count、进程 exit code

根据这些信息，给问题 **打第一个标签**：

- 「**进程挂了**」/「**内存 & GC 异常**」/「**CPU 异常**」/「**线程异常**」/「**其他**」

## 2.2 日志：看有没有 JVM 级报错

- 搜索 `OutOfMemoryError` / `StackOverflowError` / `Cannot create new native thread` 等字样；
- 看是否生成 `hs_err_pid*.log` 文件（JVM 崩溃 dump）；
- 看是否是业务自己 `System.exit` / 抛未捕获异常导致进程退出。

**如果进程挂了 → 走第 3 章；
进程活着但慢/抖/占用高 → 看 CPU / 内存 / 线程指标决定走 4/5/6 章。**

---

# 3. 分支一：进程挂掉 / 频繁重启 的决策路径

## 3.1 判定是“自己退出”还是“被杀死”

1. 看容器/系统层：
    - K8s：`kubectl describe pod` → 看 OOMKilled / Error / CrashLoopBackOff
    - 裸机：`dmesg` / `journalctl` 看是否有 OOM killer 日志
2. 看进程退出码：
    - 退出码为 137 通常是 OOMKill（SIGKILL）
    - 其它错误码看是否业务显式 exit

**分支：**

- **被系统杀死** → 重点看内存 / CPU（比如 OOMKill）
- **自己挂** → 看 JVM 错误 / 业务致命异常

## 3.2 JVM 抛 Error 导致挂：按类型分

1. **`OutOfMemoryError: Java heap space`**
    - Heap 不够用 → 进入第 4 章（内存 & GC 分支）
2. **`OutOfMemoryError: Metaspace`**
    - 类元数据空间不足 → 通常是类加载泄露 / 动态类增长过快 → 第 7 章
3. **`OutOfMemoryError: unable to create new native thread`**
    - 线程数达到系统/容器限制；
    - 排查线程数与线程栈大小配置，分析线程泄露→ 第 6 章
4. **`StackOverflowError`**
    - 深度递归 / 极深调用链；
    - 直接找调用栈，减少递归深度或改循环。

## 3.3 JVM 崩溃（hs_err_pid）

- 检查 `hs_err_pid*.log` 中：
    - 崩溃线程类型：GC 线程 / 编译线程 / 应用线程
    - 是否是本地库（JNI）访问非法内存；
- 常见原因：
    - 第三方 native 库 Bug
    - 非法内存访问
    - JVM 自身 Bug（极少）

**处理方式：**

- 先升级 JDK / 本地库到稳定版本；
- 降低风险配置（比如先关掉一些激进 JVM 选项），验证是否可稳定复现。

---

# 4. 分支二：内存 & GC 问题（Heap 相关）

典型现象：

- Heap 使用持续上升，GC 越来越频繁；
- Full GC 时间长，停顿明显；
- 接口延迟和 GC 停顿时间高度相关；
- `OutOfMemoryError: Java heap space`。

## 4.1 第一步：确认是 “GC 跟不上” 还是 “GC 正常但内存就是太大”

1. 看监控/GC 日志：
    - **老年代使用率曲线**：
        - **锯齿型**：使用率涨 → GC → 下降 → 再涨（正常）
        - **只涨不跌**：疑似内存泄露；或 live set 超过堆可承载
    - **GC 暂停总时间**：
        - 是否出现突增（一次 Full GC 很长）
2. 使用 `jstat` / GC 日志：
    - 统计 Minor / Major GC 频率与耗时
    - 看 Full GC 之间的间隔是否逐步缩短

**决策：**

- 老年代持续上涨 + Full GC 后仍下降很少 → 怀疑泄露或 live set 太大；
- GC 时间爆炸但老年代并不满 → 配置/GC 策略不合理（比如过小堆 + 频繁 GC）。

## 4.2 第二步：用 Heap Dump 分析谁占内存

1. 在高水位但还没 OOM 时：
    - `jmap -dump:live,format=b,file=heap.bin <pid>`
2. 用 MAT / VisualVM 打开：
    - 按 **Retained Size** 排序，找“大户”：
        - 大集合：`ArrayList` / `HashMap` / `ConcurrentHashMap` 等
        - 大数组：`byte[]` / `char[]`（字符串池、缓冲区）
3. 看这些大对象被谁持有：
    - 静态字段 / 单例；
    - 缓存组件；
    - ThreadLocal；
    - 框架容器（Spring context / 连接池等）。

## 4.3 第三步：判断“泄露”还是“业务确实需要这么多”

**泄露信号：**

- 业务压力无明显变化，老年代仍持续涨；
- Heap Dump 中出现：
    - 持有大量过期数据的缓存；
    - 未释放的监听器/回调；
    - ThreadLocal 持有请求上下文长时间不释放；
    - 重复的类加载器链条（热部署场景）。

**业务 live set 过大信号：**

- 每个请求/周期性任务确实需要加载大量数据在内存；
- Dump 中的大集合就是那些业务正常数据；
- 此时需要：
    - 改业务流程（分页、流式处理）；
    - 或增加内存 / 拆分服务。

## 4.4 第四步：调整策略 & 配置

1. 代码层：
    - 减少大集合常驻堆（改缓存策略 / 加容量上限 / TTL）；
    - 采用分批/游标遍历，而不是一次性载入全部；
    - 控制对象生命周期（不用静态字段长期持有）。
2. JVM 配置层：
    - 合理设置 `Xmx` / `Xms`；
    - 选择合适 GC：G1 vs ZGC（低延迟场景）；
    - 给 GC 足够的信息：
        
        比如 G1 的暂停目标、ZGC 需不要大堆。
        

---

# 5. 分支三：CPU 高 / 接口慢（但 GC 正常）

典型现象：

- 进程 CPU 接近 100%（多核则接近 N * 100%）；
- GC 时间占比不高，Heap 稳定；
- Trace/指标显示时间消耗在**应用代码**而不是 IO。

## 5.1 第一步：确认真的是 CPU Bound

1. OS 层：
    - `top` / `htop` 看进程 CPU；
2. JVM 层：
    - JFR / Prometheus 看：
        - GC 时间占比小；
        - IO 等待不高（DB/Redis 调用耗时正常）。

**确认：不是 IO 等待，不是 GC，基本是应用计算逻辑问题。**

## 5.2 第二步：用 Profiler 找热点方法

建议首选 async-profiler / JFR：

```bash
# CPU 火焰图
./profiler.sh -d 30 -e cpu -f /tmp/cpu.svg <pid>

```

在火焰图中：

- 找最宽的函数栈：通常就是热点；
- 看是你自己的业务方法、集合操作、序列化，还是某个库（比如 JSON、加解密）。

## 5.3 第三步：分析是“算法复杂度”还是“写法问题”

典型根因类别：

1. **算法/数据结构不当**：
    - `List` + 频繁 `contains` / `remove` → O(N²)
    - 大量 `sort`、重复 `distinct`/`groupingBy`
2. **无限 / 近乎无限循环**：
    - 轮询 / busy-wait 未 sleep
3. **JIT 不友好写法**：
    - 过度抽象、动态分派（多层接口、反射）
    - 在热循环里抛异常 / 使用巨大对象图
4. **过度日志 / 序列化**：
    - 高频 debug 日志；
    - 每次都全量 JSON 序列化大对象。

## 5.4 第四步：落地改造

- 对 O(N²) 类：
    - 引入 Map/Set 做索引；
    - 预聚合、预索引，减少重复扫描。
- 对热循环：
    - 删掉不必要的日志、异常；
    - 改写实现让 JIT 更易内联（减少无谓抽象）。
- 用 JMH 单独拉出算法做微基准，对比原实现 ↔ 新实现。

---

# 6. 分支四：线程 & 锁问题（死锁 / 卡住 / 线程泄露）

典型现象：

- 接口卡死或偶发极慢；
- 线程数持续增长；
- CPU 不高，但请求明显排队。

## 6.1 第一步：观察线程指标

- 线程总数曲线是否稳态还是持续上升；
- 核心线程池（例如业务线程池、定时任务线程池）的：
    - 活跃线程数
    - 队列长度
- 某些请求长时间不返回：trace 停在某一步。

## 6.2 第二步：`jstack` 抓线程栈

```bash
jstack -l <pid> > thread.dump

```

关键看：

1. 是否存在 `Found one Java-level deadlock`；
2. 大量线程处于：
    - `BLOCKED (on object monitor)`：锁争用严重；
    - `WAITING` / `TIMED_WAITING`：等待队列 / 条件变量；
    - `runnable` 但不消耗 CPU：可能在 IO。

## 6.3 第三步：典型根因模式

1. **死锁**：
    - 多锁交叉获取 → `jstack` 会直接标出死锁线程和锁；
    - 解决：调整锁顺序 / 粗化锁 / 使用无锁结构。
2. **线程泄露 / 线程数暴涨**：
    - 每次请求创建线程但不终止；
    - 自己 new Thread 而不是线程池；
    - 定时任务池不复用线程 / 不关掉。
    - 解决：统一使用线程池，明确生命周期，限制最大线程数。
3. **线程池耗尽 / 队列爆满**：
    - 队列长度接近上限，任务堆积；
    - 拒绝策略触发（抛异常 / 在调用线程执行）。
    - 解决：
        - 正确设置 core/max/queue；
        - 降低任务粒度 & 频率；
        - 对高延迟操作拆分或限流。
4. **锁竞争严重**：
    - 大量线程在同一 monitor 上 BLOCKED；
    - 热点锁挂在高频路径；
    - 解决：减小锁粒度 / 使用读写锁 / 无锁结构 / 缓存局部变量。

---

# 7. 分支五：Metaspace / 类加载 / JIT / JMM 类问题

这类问题少但一旦遇到，很“玄学”，需要单独路径。

## 7.1 Metaspace / 类加载泄露

**现象：**

- `OutOfMemoryError: Metaspace`；
- Metaspace 使用曲线持续上升，Full GC 后下降很少；
- 常伴随着：
    - 热部署 / 动态生成类（JSP、Groovy、代理、字节码增强等）。

**排查路径：**

1. 先确认是否有：
    - 自定义 ClassLoader / 热部署框架（如某些容器）；
    - 动态生成类的组件（模板引擎、脚本引擎）。
2. 使用 `jcmd`：
    - `jcmd <pid> VM.class_hierarchy` 等命令（不同 JDK 有差别）
3. Heap Dump 中：
    - 看 ClassLoader 实例数量是否异常；
    - 某些 ClassLoader 被静态字段 / 单例持有，导致其加载的所有类都不能卸载。

**治理：**

- 合理关闭 / 卸载旧 ClassLoader；
- 避免把 URLClassLoader 等挂在静态单例上不释放；
- 限制动态类生成数量、缓存策略。

---

## 7.2 JIT 相关性能抖动 / 预热问题

**现象：**

- 应用启动后前几分钟性能明显较差，后来变好（正常：JIT 预热）；
- 或运行一段时间后性能突然变差：
    - 火焰图中出现 `Interpreter` 栈增多；
    - JIT 日志出现频繁的 `deoptimized`、`made not entrant`。

**排查路径：**

1. 开 `Xlog:compiler`：
    - 看关键方法是否被编译；
    - 是否被多次反优化。
2. JFR 观察：
    - Compilation 事件；
    - Code Cache 是否接近满（Code Cache 满了会影响 JIT，新方法不能编译）。
3. 高层判断：
- 若只是预热期性能略低 → 正常；
- 若长期反复 deopt：
    - 热点方法中存在大量异常 / 类型变化；
    - 需要改写代码（减少异常、稳定类型，拆分冷门分支）。

---

## 7.3 JMM / 指令重排 导致的怪问题（只记决策点）

**典型表现：**

- 多线程下偶发：
    - 配置写好了，但有线程读不到最新值；
    - 双重检查锁定无效；
    - 状态机出现不可能状态。

**决策路径：**

1. 检查共享变量是否有：
    - `volatile` / 使用锁保护；
2. 检查是否使用了：
    - 自己手写的 lock-free 结构；
    - 非线程安全集合在多线程使用；
3. 套用 happens-before 规则：
    - 写操作对所有需要读的线程是否有明确的 hb 路径（锁、volatile、线程启动/结束等）。

**结论：**

- 缺乏 happens-before 保证的代码，即使 99.9% 正常，也要按 bug 处理；
- 对复杂并发结构，优先使用 JDK 并发包中成熟类，而不是自己拼 CAS。

---

# 8. 综合：JVM & 运行时线上问题的通用决策骨架

可以把上面内容压成一张脑内流程图：

1. **先看整体：**
    - 进程在不在？重启吗？
    - 延迟 / 错误率 / QPS 有何变化？
    - Heap / GC / CPU / 线程 指标如何？
2. **按现象归类到 5 条线：**
    - 进程挂 → 第 3 章
    - Heap & GC 异常 → 第 4 章
    - CPU 高 → 第 5 章
    - 线程问题 → 第 6 章
    - Metaspace / JIT / JMM → 第 7 章
3. **每条线内部固定三步：**
    1. 用指标/日志/轻量工具（GC 日志、jstat、jstack）确认大方向；
    2. 用重工具（Heap Dump、JFR、async-profiler）定位到函数/对象/线程；
    3. 把 root cause 映射回：
        - 代码改动（数据结构、生命周期、锁、并发模型）
        - JVM 参数改动（堆大小、GC、线程栈）
        - 架构/负载调整（限流、拆分、缓存策略）

如果你愿意，下一步可以挑一条线（比如“Heap & GC”或者“CPU & JIT”），我帮你把这条线进一步拆成：**“从报警 → 实际命令 → 典型日志 → 调整策略” 的可执行 playbook**，直接对接你现在的组件试验场。

---

# 9. JVM 故障复现实验设计（统一数据查询服务）

Binance 钱包业务的绝大多数接口都依赖「统一数据查询服务」（Unified Data Query Service, UDQS）来汇总 Spot/Margin/Funding 资产、转账记录、收益、风控参数等。该服务由一个超大缓存层 + 多源聚合层组成：

- **API**：`/api/wallet/v1/overview`、`/api/wallet/v1/history` 等，根据 `userId` 返回多种资产及估值；
- **数据源**：Redis 热缓存（Key：`wallet:{userId}`）、MySQL binlog 回放出的快照表、Kafka 资产事件流；
- **查询流程**：
  1. 读 Redis 缓存；命中直接返回；
  2. Miss 时并发拉 Spot/Margin/Funding 等表，聚合后写回缓存；
  3. 部分接口需要额外合并收益、风控指标，涉及 JSON 拼装与排序；
  4. 后台有异步任务（批量预热 / 快照重建 / 事件回放）。

为了聚焦 JVM 故障，我们把全部实验流量集中到一个虚拟的 `wallet_query/default` 实验组，并约定以下 operation（后续真正实现 service 时按此契约开发）：

| operationId | 场景 | 说明 / 关键 payload 字段 |
|-------------|------|--------------------------|
| `query_snapshot` | 核心读接口 | 模拟 `/overview`，字段：`userSegment`（hot/cold）、`assetCount`、`includeHistory`、`ttlSeconds` |
| `warm_snapshot` | 缓存预热 | 大批量拉底库构建快照，字段：`batchSize`、`userRange`、`ttlSeconds` |
| `rebuild_ledger` | 快照修复 | 读取交易流水重放，字段：`daysBack`、`retryMode` |
| `publish_bus` | Kafka 事件 | 用于广播资产更新，字段：`topic`、`payloadSize`、`partitionSkew` |

所有实验都遵循：

1. 控制面只需要一次 command，`experimentId=wallet_query`，`groupId=default`；
2. 运行中通过 Nacos 覆盖 `wallet_query/default/<operationId>` 的 JSON，动态调整缓存 TTL、资产数量、队列长度等；
3. 使用 Prometheus/Grafana + JFR/async-profiler 观察各类 JVM 指标。

> 基线 command 示例：
> ```json
> {
>   "experimentId": "wallet_query",
>   "groupId": "default",
>   "operationId": "query_snapshot",
>   "loadShape": {
>     "type": "constant",
>     "qps": 1500,
>     "concurrency": 80,
>     "durationSeconds": 1800
>   },
>   "dataRequest": {
>     "userSegment": "mixed",
>     "assetCount": 40,
>     "includeHistory": false,
>     "ttlSeconds": 30
>   }
> }
> ```
> 发起之后，只需要在 Nacos 的 `wallet_query/default/query_snapshot` 节点动态覆盖字段即可切换实验。

## 9.1 Nacos 覆盖约定

- Key：`wallet_query/default/<operationId>`；
- 常用字段：
  - `ttlSeconds`：缓存策略，影响堆内存与 GC；
  - `assetCount` / `historyRange`：聚合规模，影响 CPU/序列化；
  - `userSegment`：`"hot"`、`"cold"`、`"vip"` 等，用于在 service 内触发不同访问路径（例如读取更多风控指标）；
  - `batchSize` / `daysBack` / `retryMode`：控制后台任务强度；
  - `payloadSize` / `partitionSkew`：用于 Kafka 事件的消息体与分区倾斜。
- 动态调参流程：基线 → 注入“故障参数” → 观察 → 回滚，全程无需重新下发 command。

## 9.2 典型 JVM 故障实验矩阵（统一数据查询服务）

| # | 故障类型 | Operation | Nacos 覆盖示例 | 触发思路 | 观测要点 |
|---|----------|-----------|----------------|----------|----------|
| 1 | Heap 膨胀 / OOM | `query_snapshot` + `warm_snapshot` | `{"ttlSeconds": 1800, "userRange": {"start":100000,"count":90000}, "assetCount":120}` | 先用 `warm_snapshot` 预热 9 万用户的快照（包含 120 种资产），再把 `query_snapshot` 的 TTL 拉到 30 分钟，使缓存层与应用内 LRU 同时持有大对象，老年代只升不降，最终压迫到 `OutOfMemoryError: Java heap space`。 | `jvm_memory_used_bytes{area="heap"}`, GC Logs, `load_executor_requests_total{outcome="error"}` |
| 2 | 频繁 GC / Stop-The-World 抖动 | `query_snapshot` | `{"ttlSeconds": 2, "assetCount": 80, "includeHistory": true}` | 把 TTL 压到 2 秒并开启历史流水拼接，导致每次请求都要拼 JSON + 排序 + 回源；对象生命周期极短，Minor GC 次数飙升，P99 延迟与 GC pause 强相关。 | `jvm_gc_pause_seconds_sum/count`, `process_cpu_seconds_total`, JFR GC 事件 |
| 3 | CPU 热点 / 聚合计算瓶颈 | `query_snapshot` | `{"assetCount": 200, "includeRisk": true, "userSegment": "vip"}` | VIP 用户路径会额外读取风险敞口、收益模拟等指标；将 `assetCount` 拉到 200 并开启 `includeRisk`，放大 JSON 拼装和排序开销，可用 async-profiler 抓火焰图定位热点函数（汇率换算、BigDecimal、序列化等）。 | async-profiler `cpu`/`wall`、Grafana CPU 面板、JFR Method Sample |
| 4 | 线程池耗尽 / 下游争用 | `rebuild_ledger` | `{"daysBack": 30, "batchSize": 2000, "retryMode": "at_least_once"}` | 重建快照需要读取 30 天流水并写回 Redis/MySQL，若同时把 `load.executor.worker.maxThreads` 通过配置降到 16，再用 `rebuild_ledger` 大批量重放，可观察任务队列堆积、线程长时间 `BLOCKED` 在 Redis/MySQL 连接上，最终触发 `RejectedExecutionException`。 | Worker 池指标、`thread_pool_queue_size`, `jstack` 的 `BLOCKED` 栈 |
| 5 | Kafka 序列化 & Metaspace | `publish_bus` | `{"payloadSize": 25600, "partitionSkew": 0.98, "dynamicLoader": true}` | 通过 `publish_bus` 向资产事件总线写入 25 KB 的 payload，并开启 `dynamicLoader`（服务端可据此启用 ByteBuddy/CGLIB 生成的序列化器）；在 0.98 的分区倾斜下，CPU 与 Metaspace 同时升高，可复现 JSON/Avro 序列化热点和类加载膨胀。 | Kafka produce latency, async-profiler, `jvm_memory_used_bytes{area="nonheap",id="Metaspace"}`, JFR Class Loading |

> `dynamicLoader` 之类的布尔开关需要在 service 中实现对应逻辑（例如按需加载脚本、代理类），但运行时控制全部依赖 Nacos。

## 9.3 操作模板

1. **启动基线 run**：针对 `wallet_query/default` 的某个 operation 发出一次命令，确保指标稳定。
2. **在 Nacos 发布故障参数**：编辑对应 key 的 JSON，填写上表推荐字段。
3. **观测 5–10 分钟**：依次查看 JVM 指标、业务延迟、线程/GC 日志，并按需抓 Heap Dump、`jstack`、async-profiler、JFR。
4. **回滚**：把 JSON 改回基线或删除节点，等待曲线恢复，再分析采集数据。

这样，所有 JVM 故障复现实验都围绕同一个“Binance 钱包统一数据查询服务”展开，既贴近真实业务，又方便通过 Nacos 灵活调参验证调优思路。
