package com.example.scheduler.loadexecutor.datagenerator.onchain;

import com.example.scheduler.loadexecutor.datagenerator.onchain.model.AccountBasicMetadata;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.AccountTagSnapshot;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.DexPoolMetadata;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.TokenMetadata;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.TraderProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class OnchainMockDataGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(OnchainMockDataGeneratorService.class);

    private static final Map<Integer, ChainTopics> CHAIN_TOPICS = Map.of(
            1, new ChainTopics("ods_tx_eth", "ods_receipt_eth", "ods_dex_swap_eth"),
            42161, new ChainTopics("ods_tx_arb", "ods_receipt_arb", "ods_dex_swap_arb")
    );
    private static final String TOKEN_PRICE_TOPIC = "dim_token_price_current";
    private static final int SYNTHETIC_ACCOUNT_BATCH_SIZE = 10_000;
    private static final String[] TAG_SEGMENTS = new String[]{"whale", "lp", "mev", "retail", "farmer", "bot"};
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 implementation", e);
        }
    });

    private final OnchainMetadataRepository metadataRepository;
    private final OnchainAccountTagWriter accountTagWriter;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean pricePublisherRunning = new AtomicBoolean(false);
    private final SecureRandom secureRandom = new SecureRandom();

    public OnchainMockDataGeneratorResponse generate(OnchainMockDataGeneratorRequest request) {
        OnchainStaticData staticData = OnchainStaticData.defaults();
        Set<Integer> chainIds = new LinkedHashSet<>(request.getChainIds());
        validateChains(chainIds, staticData.availableChains());

        List<TokenMetadata> tokens = staticData.tokens(chainIds);
        List<DexPoolMetadata> pools = staticData.pools(chainIds);
        validateTokenPoolConsistency(tokens, pools);
        List<AccountTagSnapshot> accountTags = staticData.accountTags(chainIds);

        int tokenRows = request.isInitMetadata() ? metadataRepository.upsertTokens(tokens) : 0;
        int poolRows = request.isInitMetadata() ? metadataRepository.upsertPools(pools) : 0;

        AccountInitResult accountInitResult = synchronizeSyntheticAccounts(chainIds, request, staticData);
        int accountRows = accountInitResult.accountsWritten();
        int tagsWritten = accountInitResult.syntheticTagsWritten();
        if (accountInitResult.tagsRefreshed() && !accountTags.isEmpty()) {
            tagsWritten += accountTagWriter.writeTags(accountTags);
        }
        if (request.isRefreshAccountTags()) {
            accountTagWriter.scheduleRandomUpdates(request.getTagUpdatesPerSecond());
        }

        EventWriteStats eventStats = publishChainEvents(staticData, chainIds, request);

        int priceEvents = request.isProduceTokenPrices()
                ? publishTokenPrices(tokens, request.getPriceUpdateCycles())
                : 0;

        return OnchainMockDataGeneratorResponse.builder()
                .chainIds(chainIds)
                .tokensWritten(tokenRows)
                .poolsWritten(poolRows)
                .accountsWritten(accountRows)
                .tagsWritten(tagsWritten)
                .txEvents(eventStats.txEvents())
                .receiptEvents(eventStats.receiptEvents())
                .swapEvents(eventStats.swapEvents())
                .priceEvents(priceEvents)
                .generatedAt(Instant.now())
                .build();
    }

    private void validateChains(Set<Integer> requested, Set<Integer> available) {
        List<Integer> missing = requested.stream()
                .filter(chain -> !available.contains(chain))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unsupported chain ids: " + missing);
        }
        for (Integer chainId : requested) {
            if (!CHAIN_TOPICS.containsKey(chainId)) {
                throw new IllegalArgumentException("Missing topic definition for chain " + chainId);
            }
        }
    }

    private void validateTokenPoolConsistency(List<TokenMetadata> tokens, List<DexPoolMetadata> pools) {
        Map<Integer, Set<String>> tokensByChain = new HashMap<>();
        for (TokenMetadata token : tokens) {
            tokensByChain.computeIfAbsent(token.getChainId(), k -> new LinkedHashSet<>())
                    .add(token.getTokenAddress());
        }
        for (DexPoolMetadata pool : pools) {
            Set<String> chainTokens = tokensByChain.get(pool.getChainId());
            if (chainTokens == null
                    || !chainTokens.contains(pool.getToken0Address())
                    || !chainTokens.contains(pool.getToken1Address())) {
                throw new IllegalStateException(
                        "Pool " + pool.getPoolAddress() + " references missing token on chain " + pool.getChainId());
            }
        }
    }

    private AccountInitResult synchronizeSyntheticAccounts(Set<Integer> chainIds,
                                                           OnchainMockDataGeneratorRequest request,
                                                           OnchainStaticData staticData) {
        boolean initMetadata = request.isInitMetadata();
        boolean refreshTags = request.isRefreshAccountTags() || request.isInitMetadata();
        int targetPerChain = Math.max(0, request.getTagAccountTarget());
        if (!initMetadata && !refreshTags) {
            return AccountInitResult.NONE;
        }
        if (refreshTags) {
            accountTagWriter.resetChains(chainIds);
        }
        if (initMetadata) {
            metadataRepository.deleteAccountsByChains(chainIds);
        }
        if (targetPerChain <= 0) {
            return new AccountInitResult(0, 0, refreshTags);
        }
        int accountsWritten = 0;
        int tagsWritten = 0;
        for (Integer chainId : chainIds) {
            if (chainId == null) {
                continue;
            }
            long baseBlock = staticData.baseBlock(chainId);
            Instant baseTime = staticData.baseTimestamp(chainId);
            List<AccountBasicMetadata> accountBatch = initMetadata
                    ? new ArrayList<>(Math.min(SYNTHETIC_ACCOUNT_BATCH_SIZE, targetPerChain))
                    : null;
            List<AccountTagSnapshot> tagBatch = refreshTags
                    ? new ArrayList<>(Math.min(SYNTHETIC_ACCOUNT_BATCH_SIZE, targetPerChain))
                    : null;
            for (int ordinal = 1; ordinal <= targetPerChain; ordinal++) {
                String address = hashedAccountAddress(chainId, ordinal);
                if (initMetadata && accountBatch != null) {
                    accountBatch.add(buildSyntheticMetadata(chainId, address, baseBlock, baseTime, ordinal));
                    if (accountBatch.size() == SYNTHETIC_ACCOUNT_BATCH_SIZE) {
                        accountsWritten += metadataRepository.upsertAccounts(accountBatch);
                        accountBatch.clear();
                    }
                }
                if (refreshTags && tagBatch != null) {
                    tagBatch.add(buildSyntheticTag(chainId, address));
                    if (tagBatch.size() == SYNTHETIC_ACCOUNT_BATCH_SIZE) {
                        tagsWritten += accountTagWriter.writeTags(tagBatch);
                        tagBatch.clear();
                    }
                }
            }
            if (initMetadata && accountBatch != null && !accountBatch.isEmpty()) {
                accountsWritten += metadataRepository.upsertAccounts(accountBatch);
            }
            if (refreshTags && tagBatch != null && !tagBatch.isEmpty()) {
                tagsWritten += accountTagWriter.writeTags(tagBatch);
            }
        }
        return new AccountInitResult(accountsWritten, tagsWritten, refreshTags);
    }

    private AccountBasicMetadata buildSyntheticMetadata(int chainId,
                                                        String address,
                                                        long baseBlock,
                                                        Instant baseTime,
                                                        long ordinal) {
        return AccountBasicMetadata.builder()
                .chainId(chainId)
                .accountAddress(address)
                .contract(false)
                .router(false)
                .dexContract(false)
                .cexAddress(false)
                .firstSeenBlock(baseBlock + ordinal)
                .firstSeenTime(baseTime.plusSeconds(ordinal))
                .label("Synthetic Account #" + ordinal)
                .build();
    }

    private AccountTagSnapshot buildSyntheticTag(int chainId, String address) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return AccountTagSnapshot.builder()
                .chainId(chainId)
                .accountAddress(address)
                .whale(random.nextDouble() < 0.05)
                .smart(random.nextDouble() < 0.25)
                .bot(random.nextDouble() < 0.15)
                .cexDeposit(random.nextDouble() < 0.1)
                .vipLevel(random.nextInt(0, 6))
                .segment(TAG_SEGMENTS[random.nextInt(TAG_SEGMENTS.length)])
                .updatedAt(Instant.now())
                .build();
    }

    private String hashedAccountAddress(int chainId, long ordinal) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        digest.update((chainId + ":" + ordinal).getBytes(StandardCharsets.UTF_8));
        byte[] hash = digest.digest();
        String hex = Hex.encodeHexString(hash);
        return "0x" + hex.substring(0, 40);
    }

    private record AccountInitResult(int accountsWritten, int syntheticTagsWritten, boolean tagsRefreshed) {
        static final AccountInitResult NONE = new AccountInitResult(0, 0, false);
    }

    private EventWriteStats publishChainEvents(OnchainStaticData staticData,
                                               Set<Integer> chainIds,
                                               OnchainMockDataGeneratorRequest request) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Map<Integer, Long> blockCounter = new HashMap<>();
        Map<Integer, Instant> blockTime = new HashMap<>();
        chainIds.forEach(chainId -> {
            blockCounter.put(chainId, staticData.baseBlock(chainId));
            blockTime.put(chainId, staticData.baseTimestamp(chainId));
        });

        int txCount = 0;
        int receiptCount = 0;
        int swapCount = 0;

        for (Integer chainId : chainIds) {
            List<DexPoolMetadata> pools = staticData.poolsForChain(chainId);
            List<TraderProfile> traders = staticData.tradersForChain(chainId);
            if (pools.isEmpty() || traders.isEmpty()) {
                continue;
            }
            for (int i = 0; i < request.getSwapsPerChain(); i++) {
                DexPoolMetadata pool = pools.get(random.nextInt(pools.size()));
                TraderProfile trader = traders.get(random.nextInt(traders.size()));
                long blockNumber = blockCounter.compute(chainId, (k, v) -> v + 1);
                Instant timestamp = blockTime.compute(chainId, (k, v) -> v.plus(12, ChronoUnit.SECONDS));
                SwapEvent event = buildSwapEvent(chainId, pool, trader, blockNumber, timestamp, i, random);

                if (request.isIncludeTransactions()) {
                    publishTransaction(chainId, event);
                    txCount++;
                }
                if (request.isIncludeReceipts()) {
                    publishReceipt(chainId, event);
                    receiptCount++;
                }
                publishSwap(chainId, event);
                swapCount++;
                throttle(request);
            }
        }
        return new EventWriteStats(txCount, receiptCount, swapCount);
    }

    private SwapEvent buildSwapEvent(int chainId,
                                     DexPoolMetadata pool,
                                     TraderProfile trader,
                                     long blockNumber,
                                     Instant blockTimestamp,
                                     int logIndex,
                                     ThreadLocalRandom random) {
        double direction = random.nextBoolean() ? 1.0 : -1.0;
        double dollars = trader.getNotionalPreferenceUsd() *
                (0.5 + random.nextDouble(0.75));
        BigDecimal amount0Raw = BigDecimal.valueOf(direction * dollars * 10_000).setScale(0, RoundingMode.HALF_UP);
        BigDecimal amount1Raw = amount0Raw.negate()
                .multiply(BigDecimal.valueOf(random.nextDouble(0.9, 1.1)))
                .setScale(0, RoundingMode.HALF_UP);

        long gasLimit = 220000L + random.nextLong(120000L);
        long gasPrice = 25_000_000_000L + random.nextLong(30_000_000_000L);
        long nonce = random.nextLong(1000);

        return SwapEvent.builder()
                .chainId(chainId)
                .pool(pool)
                .trader(trader)
                .blockNumber(blockNumber)
                .blockTimestamp(blockTimestamp)
                .txHash(randomHash())
                .logIndex(logIndex)
                .amount0Raw(amount0Raw)
                .amount1Raw(amount1Raw)
                .gasLimit(gasLimit)
                .gasPrice(gasPrice)
                .gasUsed(gasLimit - random.nextLong(10000))
                .maxFeePerGas(gasPrice + random.nextLong(10_000_000_000L))
                .maxPriorityFeePerGas(2_000_000_000L + random.nextLong(1_000_000_000L))
                .nonce(nonce)
                .txType(2)
                .build();
    }

    private void publishTransaction(int chainId, SwapEvent event) {
        ChainTopics topics = CHAIN_TOPICS.get(chainId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chain_id", chainId);
        payload.put("tx_hash", event.getTxHash());
        payload.put("block_number", event.getBlockNumber());
        payload.put("block_timestamp", event.getBlockTimestamp().toString());
        payload.put("from_address", event.getTrader().getAccountAddress());
        payload.put("to_address", event.getPool().getRouterAddress());
        BigDecimal valueWei = BigDecimal.valueOf(event.getTrader().getNotionalPreferenceUsd())
                .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L));
        payload.put("value_wei", valueWei.toPlainString());
        payload.put("gas_limit", event.getGasLimit());
        payload.put("gas_price_wei", event.getGasPrice());
        payload.put("max_fee_per_gas_wei", event.getMaxFeePerGas());
        payload.put("max_priority_fee_per_gas_wei", event.getMaxPriorityFeePerGas());
        payload.put("nonce", event.getNonce());
        payload.put("tx_type", event.getTxType());
        byte[] input = new byte[64];
        secureRandom.nextBytes(input);
        payload.put("input_data_hex", "0x" + Hex.encodeHexString(input));
        payload.put("ingestion_time", Instant.now().toString());
        sendRecord(topics.txTopic(), event.getTxHash(), payload);
    }

    private void publishReceipt(int chainId, SwapEvent event) {
        ChainTopics topics = CHAIN_TOPICS.get(chainId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chain_id", chainId);
        payload.put("tx_hash", event.getTxHash());
        payload.put("block_number", event.getBlockNumber());
        payload.put("block_timestamp", event.getBlockTimestamp().toString());
        payload.put("status", 1);
        payload.put("gas_used", event.getGasUsed());
        payload.put("effective_gas_price_wei", event.getGasPrice());
        payload.put("contract_address", null);
        payload.put("logs_raw", "{}");
        payload.put("ingestion_time", Instant.now().toString());
        sendRecord(topics.receiptTopic(), event.getTxHash(), payload);
    }

    private void publishSwap(int chainId, SwapEvent event) {
        ChainTopics topics = CHAIN_TOPICS.get(chainId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chain_id", chainId);
        payload.put("dex_name", event.getPool().getDexName());
        payload.put("dex_version", event.getPool().getDexVersion());
        payload.put("tx_hash", event.getTxHash());
        payload.put("log_index", event.getLogIndex());
        payload.put("block_number", event.getBlockNumber());
        payload.put("block_timestamp", event.getBlockTimestamp().toString());
        payload.put("pool_address", event.getPool().getPoolAddress());
        payload.put("sender_address", event.getPool().getRouterAddress());
        payload.put("recipient_address", event.getTrader().getAccountAddress());
        payload.put("token0_address", event.getPool().getToken0Address());
        payload.put("token1_address", event.getPool().getToken1Address());
        payload.put("amount0_raw", formatBig(event.getAmount0Raw()));
        payload.put("amount1_raw", formatBig(event.getAmount1Raw()));
        if ("v3".equalsIgnoreCase(event.getPool().getDexVersion())) {
            payload.put("sqrt_price_x96", BigDecimal.valueOf(79228162514.0 + ThreadLocalRandom.current().nextDouble(1000)));
            payload.put("liquidity", BigDecimal.valueOf(5_000_000_000L + ThreadLocalRandom.current().nextLong(5_000_000_000L)).toPlainString());
            payload.put("tick", ThreadLocalRandom.current().nextInt(-50_000, 50_000));
        } else {
            payload.put("sqrt_price_x96", null);
            payload.put("liquidity", null);
            payload.put("tick", null);
        }
        payload.put("raw_event_json", "{}");
        payload.put("ingestion_time", Instant.now().toString());
        sendRecord(topics.swapTopic(), event.getTxHash(), payload);
    }

    /**
     * 发布token价格更新到Kafka
     * 策略：每个token每秒更新一次,通过在1秒内均匀分布所有token的更新时间来避免突发流量
     * 
     * 例如:如果有10个token,则:
     * - token0在第0ms更新
     * - token1在第100ms更新
     * - token2在第200ms更新
     * ...
     * - token9在第900ms更新
     * 然后下一秒重复这个模式
     * 
     * @param tokens 需要更新价格的token列表
     * @param cycles 更新轮次(每轮1秒)
     * @return 总共发送的价格记录数
     */
    private int publishTokenPrices(List<TokenMetadata> tokens, int cycles) {
        if (tokens.isEmpty() || cycles <= 0) {
            return 0;
        }
        if (!pricePublisherRunning.compareAndSet(false, true)) {
            LOG.warn("Token price publisher is already running, skipping duplicate trigger");
            return 0;
        }

        try {
            int count = 0;
            long intervalMillis = 1000L; // 每个token的更新间隔:1秒
    
            // 计算每个token在1秒内的分布间隔
            long delayPerToken = tokens.size() > 1 ? intervalMillis / tokens.size() : 0;
    
            Instant baseTime = Instant.now();
    
            for (int cycle = 0; cycle < cycles; cycle++) {
                long cycleStartMillis = System.currentTimeMillis();
    
                for (int tokenIdx = 0; tokenIdx < tokens.size(); tokenIdx++) {
                    TokenMetadata token = tokens.get(tokenIdx);
    
                    Instant updatedAt = baseTime
                            .plusSeconds(cycle)
                            .plusMillis(tokenIdx * delayPerToken);
    
                    double variance = ThreadLocalRandom.current().nextDouble(-0.02, 0.02);
                    double price = Math.max(0.0001, token.getBasePriceUsd() * (1 + variance));
                    double mcap = Math.max(1.0, token.getBaseMcapUsd() * (1 + variance));
    
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("chain_id", token.getChainId());
                    payload.put("token_address", token.getTokenAddress());
                    payload.put("price_usd", round(price));
                    payload.put("mcap_usd", round(mcap));
                    payload.put("source", "mock");
                    payload.put("updated_at", updatedAt.toString());
    
                    sendRecord(TOKEN_PRICE_TOPIC, keyForPrice(token), payload);
                    count++;
    
                    if (delayPerToken > 0 && tokenIdx < tokens.size() - 1) {
                        sleepMillis(delayPerToken);
                    }
                }
    
                if (cycle < cycles - 1) {
                    long elapsedMillis = System.currentTimeMillis() - cycleStartMillis;
                    long remainingMillis = intervalMillis - elapsedMillis;
                    if (remainingMillis > 0) {
                        sleepMillis(remainingMillis);
                    }
                }
            }
            return count;
        } finally {
            pricePublisherRunning.set(false);
        }
    }
    
    /**
     * 休眠指定毫秒数
     */
    private void sleepMillis(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void throttle(OnchainMockDataGeneratorRequest request) {
        long delay = request.getEmitDelayMillis();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendRecord(String topic, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json).get(5, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payload for topic " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish payload to topic " + topic, e);
        }
    }

    private String formatBig(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String keyForPrice(TokenMetadata token) {
        return token.getChainId() + ":" + token.getTokenAddress();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private String randomHash() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "0x" + Hex.encodeHexString(bytes);
    }

    private void sleepSeconds(long seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record ChainTopics(String txTopic, String receiptTopic, String swapTopic) {
    }

    @Value
    @Builder
    private static class SwapEvent {
        int chainId;
        DexPoolMetadata pool;
        TraderProfile trader;
        long blockNumber;
        Instant blockTimestamp;
        String txHash;
        int logIndex;
        BigDecimal amount0Raw;
        BigDecimal amount1Raw;
        long gasLimit;
        long gasPrice;
        long gasUsed;
        long maxFeePerGas;
        long maxPriorityFeePerGas;
        long nonce;
        int txType;
    }

    private record EventWriteStats(int txEvents, int receiptEvents, int swapEvents) {
    }
}
