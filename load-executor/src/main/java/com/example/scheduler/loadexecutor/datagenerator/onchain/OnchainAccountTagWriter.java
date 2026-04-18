package com.example.scheduler.loadexecutor.datagenerator.onchain;

import com.example.scheduler.loadexecutor.datasource.redis.RedisDataSource;
import com.example.scheduler.loadexecutor.datagenerator.onchain.model.AccountTagSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class OnchainAccountTagWriter {

    private final RedisDataSource redisDataSource;
    private final ObjectMapper objectMapper;

    private final Map<Integer, List<AccountRef>> chainAccountPools = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> chainAccountSets = new ConcurrentHashMap<>();
    private final List<AccountRef> globalAccountPool = Collections.synchronizedList(new ArrayList<>());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new TagThreadFactory());
    private final AtomicBoolean updaterStarted = new AtomicBoolean(false);
    private volatile int updatesPerSecond = 0;

    public int writeTags(List<AccountTagSnapshot> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AccountTagSnapshot snapshot : tags) {
            writeSnapshot(snapshot);
            remember(snapshot.getChainId(), snapshot.getAccountAddress());
            count++;
        }
        return count;
    }

    public void scheduleRandomUpdates(int updatesPerSecond) {
        this.updatesPerSecond = Math.max(0, updatesPerSecond);
        if (this.updatesPerSecond <= 0) {
            return;
        }
        if (updaterStarted.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(this::refreshRandomAccounts, 1, 1, TimeUnit.SECONDS);
        }
    }

    public void resetChains(Set<Integer> chainIds) {
        if (chainIds == null || chainIds.isEmpty()) {
            return;
        }
        for (Integer chainId : chainIds) {
            if (chainId == null) {
                continue;
            }
            clearChainState(chainId);
            deleteChainKeys(chainId);
        }
    }

    private void refreshRandomAccounts() {
        int count = this.updatesPerSecond;
        if (count <= 0) {
            return;
        }
        List<AccountRef> pool = globalAccountPool;
        if (pool.isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            AccountRef ref;
            synchronized (pool) {
                if (pool.isEmpty()) {
                    return;
                }
                ref = pool.get(random.nextInt(pool.size()));
            }
            AccountTagSnapshot snapshot = randomSnapshot(ref.chainId(), ref.address());
            writeSnapshot(snapshot);
        }
    }

    private void writeSnapshot(AccountTagSnapshot snapshot) {
        String key = snapshot.getChainId() + ":" + snapshot.getAccountAddress();
        redisDataSource.execute(template -> template.opsForValue().set(key, serialize(snapshot)));
    }

    private String serialize(AccountTagSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "chain_id", snapshot.getChainId(),
                            "account_address", snapshot.getAccountAddress(),
                            "is_whale", snapshot.isWhale(),
                            "is_smart", snapshot.isSmart(),
                            "is_bot", snapshot.isBot(),
                            "is_cex_deposit", snapshot.isCexDeposit(),
                            "vip_level", snapshot.getVipLevel(),
                            "segment", snapshot.getSegment(),
                            "updated_at", snapshot.getUpdatedAt().toString()
                    )
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize account tag snapshot", e);
        }
    }

    private void remember(int chainId, String address) {
        Set<String> seen = chainAccountSets.computeIfAbsent(chainId, k -> ConcurrentHashMap.newKeySet());
        if (!seen.add(address)) {
            return;
        }
        AccountRef ref = new AccountRef(chainId, address);
        chainAccountPools.computeIfAbsent(chainId, k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
        synchronized (globalAccountPool) {
            globalAccountPool.add(ref);
        }
    }

    private AccountTagSnapshot randomSnapshot(int chainId, String address) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return AccountTagSnapshot.builder()
                .chainId(chainId)
                .accountAddress(address)
                .whale(random.nextDouble() < 0.05)
                .smart(random.nextDouble() < 0.25)
                .bot(random.nextDouble() < 0.15)
                .cexDeposit(random.nextDouble() < 0.1)
                .vipLevel(random.nextInt(0, 6))
                .segment(randomSegment(random))
                .updatedAt(Instant.now())
                .build();
    }

    private String randomSegment(ThreadLocalRandom random) {
        String[] segments = new String[]{"whale", "lp", "mev", "retail", "farmer", "bot"};
        return segments[random.nextInt(segments.length)];
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private record AccountRef(int chainId, String address) {
    }

    private static class TagThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "account-tag-updater");
            thread.setDaemon(true);
            return thread;
        }
    }

    private void clearChainState(int chainId) {
        chainAccountPools.remove(chainId);
        chainAccountSets.remove(chainId);
        synchronized (globalAccountPool) {
            globalAccountPool.removeIf(ref -> ref.chainId() == chainId);
        }
    }

    private void deleteChainKeys(int chainId) {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(chainId + ":*")
                .count(1000)
                .build();
        redisDataSource.execute(connection -> {
            try (Cursor<byte[]> cursor = connection.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    connection.keyCommands().del(cursor.next());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to reset redis keys for chain " + chainId, e);
            }
            return null;
        });
    }
}
