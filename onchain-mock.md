
# 1. 范围 & 总体目标
本文档为原aggregator的迭代新版本
## 1.1 当前范围（MVP）

- 链：
    - Ethereum Mainnet（chain_id = 1）
    - Arbitrum One（chain_id = 42161）
- DEX：
    - Uniswap V2
    - Uniswap V3
- Token：
    - 当前只重点分析 **5 个代表性 token**（例如：ETH、USDC、USDT + 2 个代表性 alt），
        
        但整体 schema 不依赖 token 个数，可平滑扩展到全部 token。
        
- 数据来源：
    - 链上真实节点（例如 QuickNode / 自建节点），
    - **数据接入层负责从 JSON-RPC / Websocket 中做简单解析（tx、receipt、Swap 事件最小 decode）并推送到 ODS Kafka topic**。

## 1.2 目标

构建一条**实时 ODS → 实时 DWD** 的链上 DEX 交易数据链路：

- ODS：
    - 链上**交易事实流**（tx、receipt）
    - 通用 **dex_swap 事件事实流**
    - mock 的 token price / mcap 流
- DWD：
    - 统一的 `dwd_dex_swap` 事实表（Kafka topic），
        
        已经 enrich 了：
        
        - 交易发起人 / router / gas 成本
        - token / pool / account 元数据
        - 最新用户标签（user tag，最新值即可）
        - 最新价格 / mcap（来自 mock 流）

下游（不在本设计文档范围，但要服务的目标）：

- token 流分析
- token account 分布分析
- PnL 分析

---

# 2. Topic & 表设计总览

## 2.1 命名约定

- ODS：`ods_*`
- 维表 / 辅助：`dim_*`
- DWD：`dwd_*`
- 链维度：
    - Ethereum：topic 后缀 `_eth`
    - Arbitrum：topic 后缀 `_arb`

> 实际实现时，ETH 与 Arbitrum 可以是不同 Kafka topic；字段中一定包含 chain_id 以支持未来多链扩展。
> 

---

# 3. ODS 层（链上事实流）Schema

## 3.1 交易事实：`ods_tx_eth` / `ods_tx_arb`

> 从真实节点 eth_getBlockByNumber / eth_getTransactionByHash 解析后写入 Kafka。
> 

**Topic：**

- `ods_tx_eth`
- `ods_tx_arb`

**Schema（两链一致，只是 chain_id 不同）：**

```sql
ods_tx_* (
  chain_id                    INT,                -- 1 / 42161
  tx_hash                     STRING,
  block_number                BIGINT,
  block_timestamp             TIMESTAMP_LTZ(3),

  from_address                STRING,
  to_address                  STRING,

  value_wei                   DECIMAL(38, 0),
  gas_limit                   BIGINT,
  gas_price_wei               BIGINT,            -- legacy tx 可用
  max_fee_per_gas_wei         BIGINT,            -- EIP-1559
  max_priority_fee_per_gas_wei BIGINT,           -- EIP-1559
  nonce                       BIGINT,
  tx_type                     TINYINT,           -- 0,1,2...

  input_data_hex              STRING,            -- 原始 input

  ingestion_time              TIMESTAMP_LTZ(3)
)

```

---

## 3.2 交易回执：`ods_receipt_eth` / `ods_receipt_arb`

> 从真实节点 eth_getTransactionReceipt 解析。
> 

**Topic：**

- `ods_receipt_eth`
- `ods_receipt_arb`

**Schema：**

```sql
ods_receipt_* (
  chain_id                    INT,
  tx_hash                     STRING,
  block_number                BIGINT,
  block_timestamp             TIMESTAMP_LTZ(3),

  status                      TINYINT,            -- 1=成功,0=失败
  gas_used                    BIGINT,
  effective_gas_price_wei     BIGINT,             -- 实际 gas 单价

  contract_address            STRING,             -- 创建合约时非空，否则 NULL

  logs_raw                    STRING,             -- 原始 logs JSON（可选，用于调试）

  ingestion_time              TIMESTAMP_LTZ(3)
)

```

---

## 3.3 通用 DEX Swap 事件：`ods_dex_swap_eth` / `ods_dex_swap_arb`

> 数据接入层从 receipt.logs 中筛选出所有 Uniswap V2/V3 的 Swap 事件，做 minimal decode 后写入该 topic。
> 
> - V2：`Swap(address sender, uint amount0In, uint amount1In, uint amount0Out, uint amount1Out, address to)`
> - V3：`Swap(address sender, address recipient, int256 amount0, int256 amount1, uint160 sqrtPriceX96, uint128 liquidity, int24 tick)`

**Topic：**

- `ods_dex_swap_eth`
- `ods_dex_swap_arb`

**Schema：**

```sql
ods_dex_swap_* (
  chain_id                    INT,                -- 1 / 42161
  dex_name                    STRING,             -- 'uniswap'
  dex_version                 STRING,             -- 'v2' / 'v3'

  tx_hash                     STRING,
  log_index                   INT,

  block_number                BIGINT,
  block_timestamp             TIMESTAMP_LTZ(3),

  pool_address                STRING,
  sender_address              STRING,             -- event.sender
  recipient_address           STRING,             -- event.recipient / to

  -- 如上游已 decode 出池的 token，可填；否则留空，后面用 dim_dex_pool 补
  token0_address              STRING,             -- 可为 NULL
  token1_address              STRING,             -- 可为 NULL

  -- 以“池视角变化量”存放，方便多协议统一
  amount0_raw                 DECIMAL(38, 0),
  amount1_raw                 DECIMAL(38, 0),

  sqrt_price_x96              DECIMAL(38, 0),     -- v3 有效
  liquidity                   DECIMAL(38, 0),     -- v3 有效
  tick                        INT,                -- v3 有效

  raw_event_json              STRING,             -- 原始事件 JSON（可选）

  ingestion_time              TIMESTAMP_LTZ(3)
)

```

---

---

# 4. 维表设计

## 4.1 Token 静态元数据：`dim_token_basic`（Broadcast 来源）

**物理存储建议：**

- 存在一个小型 OLTP（Postgres/MySQL）中。
- 由独立同步服务周期性导出为一个 Kafka snapshot topic（如 `dim_token_basic_snapshot`），
    
    Flink Job 启动时从该 topic 加载为 Broadcast State。
    

**逻辑 Schema：**

```sql
dim_token_basic (
  chain_id                    INT,
  token_address               STRING,

  symbol                      STRING,
  name                        STRING,
  decimals                    INT,

  category                    STRING,            -- stable / bluechip / meme / ...
  is_stablecoin               BOOLEAN,
  is_bluechip                 BOOLEAN,

  created_block               BIGINT,
  created_time                TIMESTAMP_LTZ(3),

  extra_meta_json             STRING,

  PRIMARY KEY (chain_id, token_address)
)

```

---

## 4.2 DEX 池静态元数据：`dim_dex_pool`（Broadcast 来源）

**物理存储建议：**

- 同样放在 OLTP，或离线扫描 Uniswap 池导出。
- 由独立同步服务输出 Kafka snapshot topic `dim_dex_pool_snapshot`，供 Flink Broadcast 加载。

**逻辑 Schema：**

```sql
dim_dex_pool (
  chain_id                    INT,
  dex_name                    STRING,
  dex_version                 STRING,

  pool_address                STRING,

  token0_address              STRING,
  token1_address              STRING,
  fee_tier_bps                INT,               -- V3: 3000=0.3%

  created_block               BIGINT,
  created_time                TIMESTAMP_LTZ(3),

  is_active                   BOOLEAN,

  PRIMARY KEY (chain_id, pool_address)
)

```

---

## 4.3 账号基础元数据：`dim_account_basic`（Broadcast 来源，小表）

> 只存放少量“已知特殊账户”：路由、DEX合约、CEX地址等。
> 

```sql
dim_account_basic (
  chain_id                    INT,
  account_address             STRING,

  is_contract                 BOOLEAN,
  is_router                   BOOLEAN,
  is_dex_contract             BOOLEAN,
  is_cex_address              BOOLEAN,

  first_seen_block            BIGINT,
  first_seen_time             TIMESTAMP_LTZ(3),

  label                       STRING,

  PRIMARY KEY (chain_id, account_address)
)

```

---

## 4.4 用户标签：`dim_account_tag_latest`（L1 本地 cache + L2 Redis）

**物理存储：**

- L2：Redis 集群
    - Key：`<chain_id>:<account_address>`
    - Value：JSON / Hash，包含标签字段
- L1：Flink job 内部本地缓存（Caffeine / Guava），由自定义 `AsyncLookupFunction` 维护

**逻辑 Schema（仅用于说明字段）：**

```sql
dim_account_tag_latest (
  chain_id                    INT,
  account_address             STRING,

  is_whale                    BOOLEAN,
  is_smart                    BOOLEAN,
  is_bot                      BOOLEAN,
  is_cex_deposit              BOOLEAN,
  vip_level                   SMALLINT,
  segment                     STRING,

  updated_at                  TIMESTAMP_LTZ(3)
)

```

> join 语义：只需要“当前最新标签”，不需要按事件时间回看。
> 

---

## 4.5 最新价格 / mcap：`dim_token_price_current`（Kafka upsert topic）

**物理存储：**

- 由 token_price_current_mock_producer 直接写入 Kafka upsert topic dim_token_price_current
- 未来如接入真实价格源，可用 Flink/CDC/外部服务替换该 mock producer，但 **topic schema 与下游 join 逻辑保持不变**。

**逻辑 Schema：**

```sql
dim_token_price_current (
  chain_id                    INT,
  token_address               STRING,

  price_usd                   DOUBLE,
  mcap_usd                    DOUBLE,
  source                      STRING,
  updated_at                  TIMESTAMP_LTZ(3),

  PRIMARY KEY (chain_id, token_address)
)

```

---

# 5. DWD 层：统一 Swap 明细事实表 `dwd_dex_swap`

**Topic：**

- `dwd_dex_swap`（覆盖 ETH + Arbitrum，靠 `chain_id` 区分）

**Schema：**

```sql
dwd_dex_swap (
  -- 定位
  chain_id                    INT,
  dex_name                    STRING,
  dex_version                 STRING,

  tx_hash                     STRING,
  log_index                   INT,
  block_number                BIGINT,
  block_timestamp             TIMESTAMP_LTZ(3),

  -- 池 & 参与方
  pool_address                STRING,
  router_address              STRING,            -- tx.to (可能是路由)
  trader_address              STRING,            -- tx.from，真实发起人

  sender_address              STRING,            -- event.sender
  recipient_address           STRING,            -- event.recipient / to

  -- Token 维度（已通过 dim 补全）
  token0_address              STRING,
  token1_address              STRING,
  token0_symbol               STRING,
  token1_symbol               STRING,
  token0_decimals             INT,
  token1_decimals             INT,

  -- 数量（原始 + 归一化）
  amount_token0_in_raw        DECIMAL(38, 0),
  amount_token0_out_raw       DECIMAL(38, 0),
  amount_token1_in_raw        DECIMAL(38, 0),
  amount_token1_out_raw       DECIMAL(38, 0),

  amount_token0_in            DOUBLE,
  amount_token0_out           DOUBLE,
  amount_token1_in            DOUBLE,
  amount_token1_out           DOUBLE,

  -- 方向与价格（方便分析）
  base_token_address          STRING,
  quote_token_address         STRING,
  price_base_in_quote         DOUBLE,

  price_token0_usd            DOUBLE,
  price_token1_usd            DOUBLE,
  swap_value_usd              DOUBLE,

  -- gas 成本
  gas_used                    BIGINT,
  effective_gas_price_wei     BIGINT,
  gas_cost_native             DECIMAL(38, 0),
  gas_cost_usd                DOUBLE,

  -- 部分标签快照（来自 account tag 最新值）
  trader_is_whale             BOOLEAN,
  trader_is_smart             BOOLEAN,
  trader_is_bot               BOOLEAN,

  price_source                STRING,            -- 来自 dim_token_price_current.source
  account_tag_version         STRING,            -- 可选，标识打标签批次 / 版本

  ingestion_time              TIMESTAMP_LTZ(3),

  PRIMARY KEY (chain_id, tx_hash, log_index) NOT ENFORCED
)

```

> 所有下游 DWS / 分析都基于 dwd_dex_swap 作为事实表进行聚合或 join。
> 

---

# 6. Flink Job 划分与实现方式

本方案最终有 2 个“生产单元”：

1. **token_price_current_mock_producer（非 Flink Job）**
2. dex_swap_dwd_job（Flink DataStream API）=

### **Job 1：token_price_current_mock_producer（非 Flink）**

**职责：**

- 直接生成/更新 (chain_id, token_address) 的最新 price_usd / mcap_usd，写入 Kafka **upsert topic** dim_token_price_current。

**输入：**

- 无（纯 mock）

**输出：**

- Kafka Sink：dim_token_price_current（upsert 语义，即相同 key 覆盖）

**实现建议：**

- 用一个轻量的 Producer（Java / Go / Python 均可）：
    - 周期性（如 1s/5s/10s）对你选定的 token 集合产出价格与 mcap
    - 保证 updated_at 单调递增
    - source = 'mock'
- Topic 仍保持原 schema 不变（dim_token_price_current 仍是“最新价视图”）。

---

## 6.2 Job 2：`dex_swap_dwd_job`（DataStream）

**职责：**

- 消费链上事实流（tx、receipt、dex_swap）+ 各类维表，
- 通过一系列 DataStream 算子：
    - 事实–事实 interval join：swap ↔ tx/receipt
    - Broadcast enrich：token / pool / account_basic
    - Async lookup + 本地缓存：account_tag（Redis）
    - 最新价格 lookup：dim_token_price_current
- 产出统一的 `dwd_dex_swap` 事实流到 Kafka。

### 6.2.1 输入 Source

- Kafka Source：
    - `ods_dex_swap_eth`, `ods_dex_swap_arb`（可以在 job 内 union 为一个流）
    - `ods_tx_eth`, `ods_tx_arb`
    - `ods_receipt_eth`, `ods_receipt_arb`
    - `dim_token_basic_snapshot`（作为 Broadcast Source）
    - `dim_dex_pool_snapshot`（Broadcast Source）
    - `dim_account_basic_snapshot`（Broadcast Source）
    - `dim_token_price_current`（可当作普通 Kafka Source，用 mapState 维护最新价）
- Redis（L2 缓存）：
    - `dim_account_tag_latest`（通过自定义 AsyncLookupFunction）

### 6.2.2 Job 内部算子流（逻辑）

用 DataStream 写一条主流 + 多个 side 输入：

1. **Tx + Receipt 合并为 tx_enriched（事实–事实 interval join）**
    - 建两个 DataStream：
        - `txStream`（来自 `ods_tx_*` union）
        - `receiptStream`（来自 `ods_receipt_*` union）
    - 按 `(chain_id, tx_hash)` `keyBy` 后，用 `CoProcessFunction` 合并：
        - 在 keyed state 中缓存 tx / receipt（带 TTL）
        - 当 tx 与 receipt 都到齐时，输出 `tx_enriched`（包含 from/to、gasUsed、effectiveGasPrice、status 等）。
    - **实现方式**：DataStream API，方便精细控制乱序与 TTL。
2. **Swap ↔ tx_enriched interval join**
    - 从 `ods_dex_swap_*` union 得到 `swapStream`，按 `(chain_id, tx_hash)` `keyBy`。
    - 与 `tx_enriched` 做 `CoProcessFunction`：
        - 同样在 keyed state 中缓存 “swap” 或 “tx_enriched” 一侧缺失的数据
        - 对齐后输出 `swap_with_tx`，字段包含：
            - 原 swap 字段
            - trader_address（tx.from）
            - router_address（tx.to）
            - gas_used / effective_gas_price_wei 等。
3. **Broadcast 维表 enrich**
    - 为 `dim_token_basic_snapshot`、`dim_dex_pool_snapshot`、`dim_account_basic_snapshot` 各建一个 Broadcast Stream。
    - 使用 `KeyedBroadcastProcessFunction`：
        - Managing 三个 BroadcastState（token/pool/account_basic）
        - 对 `swap_with_tx` 进行 enrich：
            - 填补 token0/1 地址（从 pool 维表）
            - 填充 symbol/decimals 等
            - 对 router / pool / 合约地址进行标记（来自 account_basic）
    - 输出 `swap_enriched_static`。
4. **最新价格维护（dim_token_price_current）**
    - 从 Kafka 订阅 `dim_token_price_current`，得到 `priceStream`。
    - 在 job 内部维护：
        - keyed by `(chain_id, token_address)` 的 mapState，存 latest `price_usd`, `mcap_usd`, `source`, `updated_at`
    - 拿这个 mapState 在后续 operator 中做 price lookup。
5. **用户标签 lookup（L1 本地 cache + L2 Redis）**
    - 对 `swap_enriched_static` 使用自定义 `AsyncFunction`：
        - 先查本地 L1 cache（例如 Caffeine Map，key 为 `(chain_id, trader_address)`）
        - miss 时异步调用 Redis（L2），拿到最新标签后：
            - 回写 L1 cache
            - 把 `trader_is_whale`、`trader_is_smart`、`trader_is_bot` 等字段打在记录上。
    - 输出 `swap_with_tags`。
6. **价格 / mcap enrich + 数量归一化**
    - 对 `swap_with_tags` 做一个 `map` / `process`：
        - 使用前面维护的 `priceState`：
            - 查 `token0`/`token1` 最新价格 / mcap（没有则可打默认或空）
        - 根据 `decimals` 把 `amount*_raw` 转成 `amount*`（double）
        - 按约定的 base / quote（例如统一视角：
            - 如果 pair 中含稳定币，则稳定币做 quote；
            - 否则按某个 token 白名单规则）
            - 计算 `price_base_in_quote` 和 `swap_value_usd`
        - 计算 `gas_cost_native`、`gas_cost_usd`。
    - 输出最终的 `dwd_dex_swap` 记录。
7. **Sink 到 Kafka**
    - 将完整的 `dwd_dex_swap` 流写入 Kafka topic `dwd_dex_swap`。

### 6.2.3 Job 内时间语义

- 事件时间基准：
    - 统一使用 `block_timestamp` 作为 event-time
- interval join 与状态 TTL：
    - tx / receipt / swap 的对齐窗口：例如 ±5 分钟
    - state TTL 设置略大于该窗口，防止迟到数据导致 join 失败

---

# 7. 关键设计决策总结（便于 agent coding 抓重点）

1. **范围**：
    - 当前只处理 ETH + Arbitrum 上 Uniswap V2/V3 的真实链上数据，
    - 但所有命名与 schema 均以 `dex_swap`、`chain_id` 等通用字段设计，对后续多链、多 DEX 扩展透明。
2. **数据接入层职责**：
    - 从节点（QuickNode / 自建）获取 tx / receipt / logs，
    - 做 minimal decode（tx、receipt、Swap 事件），
    - **直接写入本设计中的 ODS Kafka topic**，确保 Flink Job 不需要理解 JSON-RPC 细节。
3. **ODS → DWD 路径固定为 Kafka → Flink DataStream/SQL → Kafka：**
    - 所有中间与最终结果均写回 Kafka：
        - `ods_*` → `dim_token_price_current`（Job1）
        - `ods_*` + `dim_*` → `dwd_dex_swap`（Job2）
4. **维度 enrich 策略：**
    - 静态 / 极低频维表（token、pool、account_basic） → Kafka snapshot + Broadcast State
    - 用户标签 → L1 本地缓存 + L2 Redis Async Lookup，**只用最新值**
    - 高频价格 / mcap → 独立 Job 产生 `dim_token_price_current`，在主 Job 中以最新值 state 方式 lookup
5. **Flink Job 划分与技术选型：**
    - `token_price_current_job`：
        - 实现方式：**Flink SQL / Table API**（简单 last-value group-by）
    - `dex_swap_dwd_job`：
        - 实现方式：**Flink DataStream API**
        - 使用的能力包括：
            - 多 Kafka Source union
            - keyed `CoProcessFunction` 做 tx & receipt & swap 的事实–事实 join
            - `KeyedBroadcastProcessFunction` 实现 Broadcast 维表 enrich
            - `AsyncFunction` + 本地缓存 + Redis 实现标签 lookup
            - map/process 进行价格 lookup、数量归一化、派生字段计算

---

# 8. Mock 数据生成器（load-executor 集成）

`load-executor` 的 `datagenerator` 模块已经内置 `/datagenerator/onchain/mock` 接口，用于一键完成文档内所有源表与 topic 的造数：

- MySQL 维表：`dim_token_basic`、`dim_dex_pool`、`dim_account_basic`，采用 Upsert 方式写入。
- Redis：`dim_account_tag_latest` 以 `<chain_id>:<account_address>` 作为 key 写入最新标签。
- Kafka topic：
    - ODS：`ods_tx_eth`、`ods_receipt_eth`、`ods_dex_swap_eth` 以及对应的 Arbitrum topic。
    - 维度快照：`dim_token_basic_snapshot`、`dim_dex_pool_snapshot`、`dim_account_basic_snapshot`。
    - 价格：`dim_token_price_current`（同 `token_price_current_mock_producer` 语义）。

示例请求：

```bash
curl -X POST http://localhost:18082/datagenerator/onchain/mock \
  -H "Content-Type: application/json" \
  -d '{
    "chainIds": [1, 42161],
    "swapsPerChain": 120,
    "initMetadata": true,
    "refreshAccountTags": true,
    "produceTokenPrices": true,
    "priceUpdateCycles": 12,
    "emitDelayMillis": 50,
    "tagAccountTarget": 100000,
    "tagUpdatesPerSecond": 100
  }'
```

关键参数：

- `chainIds`：需要覆盖的链（默认为 ETH 主网与 Arbitrum）。
- `swapsPerChain`：每条链生成的 swap/tx/receipt 数量。
- `initMetadata`：控制是否写 Postgres 维表（dim_token/dim_pool/dim_account 等）。
- `refreshAccountTags`：是否回写 Redis 中的用户标签。
- `produceTokenPrices` + `priceUpdateCycles`：是否生成价格/市值流，以及循环次数（每个 token 固定 1s 更新一次）。
- `emitDelayMillis`：相邻 swap 事件节流毫秒数（默认 50ms，大约是之前速率的 1/5）。
- `tagAccountTarget` & `tagUpdatesPerSecond`：Redis 中维护的标签账户数量（默认 10 万）和每秒随机刷新次数（默认 100）。标签只写 Redis，不再走 Kafka。
- 元数据 snapshot topic 已停用，链上维表仍写 Postgres，Flink 侧通过 Broadcast 维持静态元数据即可。

接口返回值包含各类写入的行数/事件数，便于校验与监控。该能力直接复用 load-executor 现有 Kafka/MySQL/Redis 连接参数，可通过环境变量配置访问目标集群。

控制面 / 前端集成：

- load-executor 的 `/experiments` 现在暴露 `onchain_mock/mock_dwd` 分组，包含 `seed_mock` 操作，OperationType=INIT_DATA。
- 控制面 UI 可直接列出该实验并下发 payload（与 `/datagenerator/onchain/mock` 请求结构相同），无需额外硬编码。
- mock 服务的维表写入已切换为 Postgres（`twilight` 数据库下的 `dim_token_basic`/`dim_dex_pool`/`dim_account_basic` 等表），可直接被 DataPlatform 的 realtime pipeline 使用。
