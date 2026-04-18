package com.example.scheduler.loadexecutor.datagenerator.onchain;

import com.example.scheduler.loadexecutor.datagenerator.onchain.model.AccountBasicMetadata;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.DexPoolMetadata;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.TokenMetadata;
import com.example.scheduler.loadexecutor.datasource.postgres.PostgresDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class OnchainMetadataRepository {

    private final PostgresDataSource dataSource;

    public int upsertTokens(List<TokenMetadata> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO dim_token_basic
                (chain_id, token_address, symbol, name, decimals, category, is_stablecoin, is_bluechip,
                 created_block, created_time, extra_meta_json)
                VALUES (:chainId, :tokenAddress, :symbol, :name, :decimals, :category, :isStablecoin, :isBluechip,
                        :createdBlock, :createdTime, :extraMetaJson)
                ON CONFLICT (chain_id, token_address) DO UPDATE SET
                    symbol = EXCLUDED.symbol,
                    name = EXCLUDED.name,
                    decimals = EXCLUDED.decimals,
                    category = EXCLUDED.category,
                    is_stablecoin = EXCLUDED.is_stablecoin,
                    is_bluechip = EXCLUDED.is_bluechip,
                    extra_meta_json = EXCLUDED.extra_meta_json
                """;
        return dataSource.query(jdbc -> {
            int[] updated = jdbc.batchUpdate(sql, tokens.stream()
                    .map(token -> new MapSqlParameterSource()
                            .addValue("chainId", token.getChainId())
                            .addValue("tokenAddress", token.getTokenAddress())
                            .addValue("symbol", token.getSymbol())
                            .addValue("name", token.getName())
                            .addValue("decimals", token.getDecimals())
                            .addValue("category", token.getCategory())
                            .addValue("isStablecoin", token.isStablecoin())
                            .addValue("isBluechip", token.isBluechip())
                            .addValue("createdBlock", token.getCreatedBlock())
                            .addValue("createdTime", Timestamp.from(token.getCreatedTime()))
                            .addValue("extraMetaJson", jsonb(token.getExtraMetaJson())))
                    .toArray(MapSqlParameterSource[]::new));
            return Arrays.stream(updated).sum();
        });
    }

    public int upsertPools(List<DexPoolMetadata> pools) {
        if (pools == null || pools.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO dim_dex_pool
                (chain_id, dex_name, dex_version, pool_address, token0_address, token1_address, fee_tier_bps,
                 created_block, created_time, is_active)
                VALUES (:chainId, :dexName, :dexVersion, :poolAddress, :token0Address, :token1Address, :feeTierBps,
                        :createdBlock, :createdTime, :isActive)
                ON CONFLICT (chain_id, pool_address) DO UPDATE SET
                    dex_name = EXCLUDED.dex_name,
                    dex_version = EXCLUDED.dex_version,
                    token0_address = EXCLUDED.token0_address,
                    token1_address = EXCLUDED.token1_address,
                    fee_tier_bps = EXCLUDED.fee_tier_bps,
                    is_active = EXCLUDED.is_active
                """;
        return dataSource.query(jdbc -> {
            int[] updated = jdbc.batchUpdate(sql, pools.stream()
                    .map(pool -> new MapSqlParameterSource()
                            .addValue("chainId", pool.getChainId())
                            .addValue("dexName", pool.getDexName())
                            .addValue("dexVersion", pool.getDexVersion())
                            .addValue("poolAddress", pool.getPoolAddress())
                            .addValue("token0Address", pool.getToken0Address())
                            .addValue("token1Address", pool.getToken1Address())
                            .addValue("feeTierBps", pool.getFeeTierBps())
                            .addValue("createdBlock", pool.getCreatedBlock())
                            .addValue("createdTime", Timestamp.from(pool.getCreatedTime()))
                            .addValue("isActive", pool.isActive()))
                    .toArray(MapSqlParameterSource[]::new));
            return Arrays.stream(updated).sum();
        });
    }

    public int upsertAccounts(List<AccountBasicMetadata> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO dim_account_basic
                (chain_id, account_address, is_contract, is_router, is_dex_contract, is_cex_address,
                 first_seen_block, first_seen_time, label)
                VALUES (:chainId, :accountAddress, :isContract, :isRouter, :isDexContract, :isCexAddress,
                        :firstSeenBlock, :firstSeenTime, :label)
                ON CONFLICT (chain_id, account_address) DO UPDATE SET
                    is_contract = EXCLUDED.is_contract,
                    is_router = EXCLUDED.is_router,
                    is_dex_contract = EXCLUDED.is_dex_contract,
                    is_cex_address = EXCLUDED.is_cex_address,
                    label = EXCLUDED.label
                """;
        return dataSource.query(jdbc -> {
            int[] updated = jdbc.batchUpdate(sql, accounts.stream()
                    .map(account -> new MapSqlParameterSource()
                            .addValue("chainId", account.getChainId())
                            .addValue("accountAddress", account.getAccountAddress())
                            .addValue("isContract", account.isContract())
                            .addValue("isRouter", account.isRouter())
                            .addValue("isDexContract", account.isDexContract())
                            .addValue("isCexAddress", account.isCexAddress())
                            .addValue("firstSeenBlock", account.getFirstSeenBlock())
                            .addValue("firstSeenTime", Timestamp.from(account.getFirstSeenTime()))
                            .addValue("label", account.getLabel()))
                    .toArray(MapSqlParameterSource[]::new));
            return Arrays.stream(updated).sum();
        });
    }

    public int deleteAccountsByChains(Set<Integer> chainIds) {
        if (chainIds == null || chainIds.isEmpty()) {
            return 0;
        }
        String sql = "DELETE FROM dim_account_basic WHERE chain_id IN (:chainIds)";
        return dataSource.query(jdbc -> jdbc.update(sql, new MapSqlParameterSource("chainIds", chainIds)));
    }

    private Object jsonb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            org.postgresql.util.PGobject json = new org.postgresql.util.PGobject();
            json.setType("jsonb");
            json.setValue(value);
            return json;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Invalid json payload", e);
        }
    }
}
