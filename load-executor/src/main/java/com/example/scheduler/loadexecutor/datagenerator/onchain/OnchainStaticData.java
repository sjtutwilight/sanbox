package com.example.scheduler.loadexecutor.datagenerator.onchain;

import com.example.scheduler.loadexecutor.datagenerator.onchain.model.AccountTagSnapshot;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.DexPoolMetadata;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.TokenMetadata;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.TraderProfile;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class OnchainStaticData {

    private final Map<Integer, List<TokenMetadata>> tokensByChain;
    private final Map<Integer, List<DexPoolMetadata>> poolsByChain;
    private final Map<Integer, List<AccountTagSnapshot>> accountTagsByChain;
    private final Map<Integer, List<TraderProfile>> tradersByChain;
    private final Map<Integer, Long> baseBlockByChain;
    private final Map<Integer, Instant> baseTimestampByChain;

    private OnchainStaticData() {
        this.tokensByChain = initTokens();
        this.poolsByChain = initPools();
        this.tradersByChain = initTraders();
        this.accountTagsByChain = initAccountTags();
        this.baseBlockByChain = Map.of(
                1, 19200000L,
                42161, 128000000L
        );
        this.baseTimestampByChain = Map.of(
                1, Instant.parse("2024-04-01T00:00:00Z"),
                42161, Instant.parse("2024-04-01T00:00:00Z")
        );
    }

    static OnchainStaticData defaults() {
        return Holder.INSTANCE;
    }

    Set<Integer> availableChains() {
        return tokensByChain.keySet();
    }

    List<TokenMetadata> tokens(Set<Integer> chains) {
        return chains.stream()
                .flatMap(chain -> tokensByChain.getOrDefault(chain, List.of()).stream())
                .collect(Collectors.toList());
    }

    List<DexPoolMetadata> pools(Set<Integer> chains) {
        return chains.stream()
                .flatMap(chain -> poolsByChain.getOrDefault(chain, List.of()).stream())
                .collect(Collectors.toList());
    }

    List<AccountTagSnapshot> accountTags(Set<Integer> chains) {
        return chains.stream()
                .flatMap(chain -> accountTagsByChain.getOrDefault(chain, List.of()).stream())
                .collect(Collectors.toList());
    }

    List<TraderProfile> traders(Set<Integer> chains) {
        return chains.stream()
                .flatMap(chain -> tradersByChain.getOrDefault(chain, List.of()).stream())
                .collect(Collectors.toList());
    }

    List<TraderProfile> tradersForChain(int chainId) {
        return tradersByChain.getOrDefault(chainId, List.of());
    }

    List<DexPoolMetadata> poolsForChain(int chainId) {
        return poolsByChain.getOrDefault(chainId, List.of());
    }

    long baseBlock(int chainId) {
        return baseBlockByChain.getOrDefault(chainId, 0L);
    }

    Instant baseTimestamp(int chainId) {
        return baseTimestampByChain.getOrDefault(chainId, Instant.EPOCH);
    }

    private Map<Integer, List<TokenMetadata>> initTokens() {
        Map<Integer, List<TokenMetadata>> tokens = new HashMap<>();
        tokens.put(1, List.of(
                TokenMetadata.builder()
                        .chainId(1)
                        .tokenAddress(n("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))
                        .symbol("WETH")
                        .name("Wrapped Ether")
                        .decimals(18)
                        .category("bluechip")
                        .stablecoin(false)
                        .bluechip(true)
                        .createdBlock(4719568)
                        .createdTime(Instant.parse("2017-12-08T00:00:00Z"))
                        .extraMetaJson("{\"coingeckoId\":\"weth\"}")
                        .basePriceUsd(3200.0)
                        .baseMcapUsd(380_000_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(1)
                        .tokenAddress(n("0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"))
                        .symbol("USDC")
                        .name("USD Coin")
                        .decimals(6)
                        .category("stable")
                        .stablecoin(true)
                        .bluechip(false)
                        .createdBlock(6082465)
                        .createdTime(Instant.parse("2018-09-10T00:00:00Z"))
                        .extraMetaJson("{\"issuer\":\"Circle\"}")
                        .basePriceUsd(1.0)
                        .baseMcapUsd(32_000_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(1)
                        .tokenAddress(n("0xdAC17F958D2ee523a2206206994597C13D831ec7"))
                        .symbol("USDT")
                        .name("Tether USD")
                        .decimals(6)
                        .category("stable")
                        .stablecoin(true)
                        .bluechip(false)
                        .createdBlock(4634748)
                        .createdTime(Instant.parse("2017-11-28T00:00:00Z"))
                        .extraMetaJson("{\"issuer\":\"Tether\"}")
                        .basePriceUsd(1.0)
                        .baseMcapUsd(90_000_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(1)
                        .tokenAddress(n("0xB50721BCf8d664c30412cfbc6cf7a15145234ad1"))
                        .symbol("ARB")
                        .name("Arbitrum")
                        .decimals(18)
                        .category("alt")
                        .stablecoin(false)
                        .bluechip(false)
                        .createdBlock(16890400)
                        .createdTime(Instant.parse("2023-03-23T00:00:00Z"))
                        .extraMetaJson("{\"airdrop\":\"yes\"}")
                        .basePriceUsd(1.1)
                        .baseMcapUsd(14_000_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(1)
                        .tokenAddress(n("0x514910771AF9Ca656af840dff83E8264EcF986CA"))
                        .symbol("LINK")
                        .name("Chainlink")
                        .decimals(18)
                        .category("oracle")
                        .stablecoin(false)
                        .bluechip(true)
                        .createdBlock(4276785)
                        .createdTime(Instant.parse("2017-09-19T00:00:00Z"))
                        .extraMetaJson("{\"sector\":\"data\"}")
                        .basePriceUsd(15.5)
                        .baseMcapUsd(8_600_000_000.0)
                        .build()
        ));

        tokens.put(42161, List.of(
                TokenMetadata.builder()
                        .chainId(42161)
                        .tokenAddress(n("0x82af49447d8a07e3bd95bd0d56f35241523fbab1"))
                        .symbol("WETH")
                        .name("Wrapped Ether")
                        .decimals(18)
                        .category("bluechip")
                        .stablecoin(false)
                        .bluechip(true)
                        .createdBlock(22278)
                        .createdTime(Instant.parse("2021-08-31T00:00:00Z"))
                        .extraMetaJson("{\"bridge\":\"Arbitrum\"}")
                        .basePriceUsd(3200.0)
                        .baseMcapUsd(380_000_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(42161)
                        .tokenAddress(n("0xff970a61a04b1ca14834a43f5de4533ebddb5cc8"))
                        .symbol("USDC.e")
                        .name("USD Coin (bridged)")
                        .decimals(6)
                        .category("stable")
                        .stablecoin(true)
                        .bluechip(false)
                        .createdBlock(71737)
                        .createdTime(Instant.parse("2021-09-20T00:00:00Z"))
                        .extraMetaJson("{\"bridge\":\"canonical\"}")
                        .basePriceUsd(1.0)
                        .baseMcapUsd(3_500_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(42161)
                        .tokenAddress(n("0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9"))
                        .symbol("USDT")
                        .name("Tether USD")
                        .decimals(6)
                        .category("stable")
                        .stablecoin(true)
                        .bluechip(false)
                        .createdBlock(11730)
                        .createdTime(Instant.parse("2021-08-29T00:00:00Z"))
                        .extraMetaJson("{\"bridge\":\"canonical\"}")
                        .basePriceUsd(1.0)
                        .baseMcapUsd(1_800_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(42161)
                        .tokenAddress(n("0x912ce59144191c1204e64559fe8253a0e49e6548"))
                        .symbol("ARB")
                        .name("Arbitrum")
                        .decimals(18)
                        .category("alt")
                        .stablecoin(false)
                        .bluechip(false)
                        .createdBlock(70767448)
                        .createdTime(Instant.parse("2023-03-23T00:00:00Z"))
                        .extraMetaJson("{\"airdrop\":\"yes\"}")
                        .basePriceUsd(1.1)
                        .baseMcapUsd(14_000_000_000.0)
                        .build(),
                TokenMetadata.builder()
                        .chainId(42161)
                        .tokenAddress(n("0xfc5a1a6eb076a8a64f81e18cdf0039f05c864e5"))
                        .symbol("GMX")
                        .name("GMX")
                        .decimals(18)
                        .category("defi")
                        .stablecoin(false)
                        .bluechip(false)
                        .createdBlock(16901108)
                        .createdTime(Instant.parse("2021-09-01T00:00:00Z"))
                        .extraMetaJson("{\"sector\":\"perp\"}")
                        .basePriceUsd(45.0)
                        .baseMcapUsd(410_000_000.0)
                        .build()
        ));
        return Collections.unmodifiableMap(tokens);
    }

    private Map<Integer, List<DexPoolMetadata>> initPools() {
        Map<Integer, List<DexPoolMetadata>> pools = new HashMap<>();
        pools.put(1, List.of(
                DexPoolMetadata.builder()
                        .chainId(1)
                        .dexName("uniswap")
                        .dexVersion("v3")
                        .poolAddress(n("0x88e6a0c2ddd26feeb64f039a2c41296fcb3f5640"))
                        .token0Address(n("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))
                        .token1Address(n("0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"))
                        .feeTierBps(3000)
                        .createdBlock(12645000)
                        .createdTime(Instant.parse("2021-05-05T00:00:00Z"))
                        .active(true)
                        .routerAddress(n("0xE592427A0AEce92De3Edee1F18E0157C05861564"))
                        .build(),
                DexPoolMetadata.builder()
                        .chainId(1)
                        .dexName("uniswap")
                        .dexVersion("v3")
                        .poolAddress(n("0x4e68ccd3e89f51c3074ca5072bbac773960dfa36"))
                        .token0Address(n("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))
                        .token1Address(n("0xdAC17F958D2ee523a2206206994597C13D831ec7"))
                        .feeTierBps(3000)
                        .createdBlock(12645010)
                        .createdTime(Instant.parse("2021-05-05T00:10:00Z"))
                        .active(true)
                        .routerAddress(n("0xE592427A0AEce92De3Edee1F18E0157C05861564"))
                        .build(),
                DexPoolMetadata.builder()
                        .chainId(1)
                        .dexName("uniswap")
                        .dexVersion("v3")
                        .poolAddress(n("0xC31e54C7a869b9Cb5Be9A5Cc38919D6316a043b2"))
                        .token0Address(n("0xB50721BCf8d664c30412cfbc6cf7a15145234ad1"))
                        .token1Address(n("0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"))
                        .feeTierBps(3000)
                        .createdBlock(17000000)
                        .createdTime(Instant.parse("2023-03-24T00:00:00Z"))
                        .active(true)
                        .routerAddress(n("0x68b3465833Fb72A70ecDF485E0e4C7bD8665Fc45"))
                        .build(),
                DexPoolMetadata.builder()
                        .chainId(1)
                        .dexName("uniswap")
                        .dexVersion("v2")
                        .poolAddress(n("0xa210456b5b86c90141462ad8bd360256f0042b79"))
                        .token0Address(n("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"))
                        .token1Address(n("0x514910771AF9Ca656af840dff83E8264EcF986CA"))
                        .feeTierBps(3000)
                        .createdBlock(10145000)
                        .createdTime(Instant.parse("2020-06-01T00:00:00Z"))
                        .active(true)
                        .routerAddress(n("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"))
                        .build()
        ));

        pools.put(42161, List.of(
                DexPoolMetadata.builder()
                        .chainId(42161)
                        .dexName("uniswap")
                        .dexVersion("v3")
                        .poolAddress(n("0x9Db9e0e53058c89E5B94E29621a205198648425B"))
                        .token0Address(n("0x82af49447d8a07e3bd95bd0d56f35241523fbab1"))
                        .token1Address(n("0xff970a61a04b1ca14834a43f5de4533ebddb5cc8"))
                        .feeTierBps(500)
                        .createdBlock(31234567)
                        .createdTime(Instant.parse("2022-09-01T00:00:00Z"))
                        .active(true)
                        .routerAddress(n("0x68b3465833Fb72A70ecDF485E0e4C7bD8665Fc45"))
                        .build(),
                DexPoolMetadata.builder()
                        .chainId(42161)
                        .dexName("uniswap")
                        .dexVersion("v3")
                        .poolAddress(n("0x905dfCD5649217c42684f23958568e533C711Aa3"))
                        .token0Address(n("0x82af49447d8a07e3bd95bd0d56f35241523fbab1"))
                        .token1Address(n("0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9"))
                        .feeTierBps(3000)
                        .createdBlock(31234580)
                        .createdTime(Instant.parse("2022-09-01T00:15:00Z"))
                        .active(true)
                        .routerAddress(n("0x68b3465833Fb72A70ecDF485E0e4C7bD8665Fc45"))
                        .build(),
                DexPoolMetadata.builder()
                        .chainId(42161)
                        .dexName("uniswap")
                        .dexVersion("v3")
                        .poolAddress(n("0xCb4741B8fCF34af5aD5A5f24f1e8198f4C71dE4A"))
                        .token0Address(n("0x912ce59144191c1204e64559fe8253a0e49e6548"))
                        .token1Address(n("0xff970a61a04b1ca14834a43f5de4533ebddb5cc8"))
                        .feeTierBps(3000)
                        .createdBlock(90000000)
                        .createdTime(Instant.parse("2023-03-24T00:00:00Z"))
                        .active(true)
                        .routerAddress(n("0x68b3465833Fb72A70ecDF485E0e4C7bD8665Fc45"))
                        .build(),
                DexPoolMetadata.builder()
                        .chainId(42161)
                        .dexName("uniswap")
                        .dexVersion("v3")
                        .poolAddress(n("0x3D6a0D8AA41d0996F1272c30fEbBA4c00e27A4d8"))
                        .token0Address(n("0xfc5a1a6eb076a8a64f81e18cdf0039f05c864e5"))
                        .token1Address(n("0xff970a61a04b1ca14834a43f5de4533ebddb5cc8"))
                        .feeTierBps(10000)
                        .createdBlock(94000000)
                        .createdTime(Instant.parse("2023-11-01T00:00:00Z"))
                        .active(true)
                        .routerAddress(n("0x68b3465833Fb72A70ecDF485E0e4C7bD8665Fc45"))
                        .build()
        ));
        return Collections.unmodifiableMap(pools);
    }

    private Map<Integer, List<TraderProfile>> initTraders() {
        Map<Integer, List<TraderProfile>> traders = new HashMap<>();
        traders.put(1, List.of(
                trader(1, "0x6Fb2f5E3d7AfC86C51Bac0B1D817c0F2c2A9bFD2", "Smart LP", 500_000.0,
                        AccountTagSnapshot.builder()
                                .chainId(1)
                                .accountAddress(n("0x6Fb2f5E3d7AfC86C51Bac0B1D817c0F2c2A9bFD2"))
                                .whale(true)
                                .smart(true)
                                .bot(false)
                                .cexDeposit(false)
                                .vipLevel(3)
                                .segment("lp")
                                .updatedAt(Instant.parse("2024-04-01T00:00:00Z"))
                                .build()),
                trader(1, "0x44b0975093f1F179B1F07687C7B5a8B0C5f8B176", "Arb MEV", 300_000.0,
                        AccountTagSnapshot.builder()
                                .chainId(1)
                                .accountAddress(n("0x44b0975093f1F179B1F07687C7B5a8B0C5f8B176"))
                                .whale(false)
                                .smart(true)
                                .bot(true)
                                .cexDeposit(false)
                                .vipLevel(2)
                                .segment("mev")
                                .updatedAt(Instant.parse("2024-04-02T00:00:00Z"))
                                .build()),
                trader(1, "0xfcedca9E45605C2db10dAdb590F1bECc74df2396", "Retail Flow", 25_000.0,
                        AccountTagSnapshot.builder()
                                .chainId(1)
                                .accountAddress(n("0xfcedca9E45605C2db10dAdb590F1bECc74df2396"))
                                .whale(false)
                                .smart(false)
                                .bot(false)
                                .cexDeposit(false)
                                .vipLevel(1)
                                .segment("retail")
                                .updatedAt(Instant.parse("2024-04-03T00:00:00Z"))
                                .build())
        ));

        traders.put(42161, List.of(
                trader(42161, "0x1b90677072A5bbF35018ced72C62f51b4B0d9570", "Arb Whale", 400_000.0,
                        AccountTagSnapshot.builder()
                                .chainId(42161)
                                .accountAddress(n("0x1b90677072A5bbF35018ced72C62f51b4B0d9570"))
                                .whale(true)
                                .smart(true)
                                .bot(false)
                                .cexDeposit(false)
                                .vipLevel(4)
                                .segment("whale")
                                .updatedAt(Instant.parse("2024-04-03T00:00:00Z"))
                                .build()),
                trader(42161, "0xccee5E7Ab3edDa40c93cF6ed8F80D139496A5965", "Yield Farmer", 120_000.0,
                        AccountTagSnapshot.builder()
                                .chainId(42161)
                                .accountAddress(n("0xccee5E7Ab3edDa40c93cF6ed8F80D139496A5965"))
                                .whale(false)
                                .smart(true)
                                .bot(false)
                                .cexDeposit(false)
                                .vipLevel(2)
                                .segment("farmer")
                                .updatedAt(Instant.parse("2024-04-04T00:00:00Z"))
                                .build()),
                trader(42161, "0xB16b4dA9EABc2aF5bf0C02D16bB5Cb6407f684C2", "Bot Cluster", 60_000.0,
                        AccountTagSnapshot.builder()
                                .chainId(42161)
                                .accountAddress(n("0xB16b4dA9EABc2aF5bf0C02D16bB5Cb6407f684C2"))
                                .whale(false)
                                .smart(false)
                                .bot(true)
                                .cexDeposit(false)
                                .vipLevel(1)
                                .segment("bot")
                                .updatedAt(Instant.parse("2024-04-04T12:00:00Z"))
                                .build())
        ));

        return Collections.unmodifiableMap(traders);
    }

    private TraderProfile trader(int chainId, String address, String label, double notional, AccountTagSnapshot tag) {
        return TraderProfile.builder()
                .chainId(chainId)
                .accountAddress(n(address))
                .label(label)
                .notionalPreferenceUsd(notional)
                .tagSnapshot(tag)
                .build();
    }

    private Map<Integer, List<AccountTagSnapshot>> initAccountTags() {
        Map<Integer, List<AccountTagSnapshot>> tags = new HashMap<>();
        tradersByChain.forEach((chainId, profiles) -> tags.put(chainId,
                profiles.stream().map(TraderProfile::getTagSnapshot).collect(Collectors.toUnmodifiableList())));
        return Collections.unmodifiableMap(tags);
    }

    private static String n(String address) {
        return address.toLowerCase();
    }

    private static final class Holder {
        private static final OnchainStaticData INSTANCE = new OnchainStaticData();
    }
}
