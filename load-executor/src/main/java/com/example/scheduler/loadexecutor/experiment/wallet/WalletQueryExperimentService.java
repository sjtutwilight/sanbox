package com.example.scheduler.loadexecutor.experiment.wallet;

import com.example.scheduler.loadexecutor.experiment.OperationInvocationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletQueryExperimentService {

    private final WalletSnapshotCache cache;
    private final WalletSnapshotBuilder builder;
    private final WalletExperimentProperties properties;
    private final WalletSnapshotRetainer snapshotRetainer;
    private final WalletLedgerRepository ledgerRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> querySnapshot(OperationInvocationContext context) {
        WalletQueryRequest request = WalletQueryRequest.from(context);
        Duration ttl = request.getTtlOverride() != null ? request.getTtlOverride() : properties.getDefaultTtl();
        WalletSnapshot snapshot = cache.get(request.getUserId())
                .orElseGet(() -> buildAndCache(request, ttl));
        if (request.isRetainSnapshots()) {
            snapshotRetainer.retain(snapshot, request.getRetainPerUser(), request.getRetainGlobal());
        }
        Map<String, Object> response = new HashMap<>();
        response.put("userId", snapshot.getUserId());
        response.put("assetCount", snapshot.getAssets().size());
        response.put("historyCount", snapshot.getHistory().size());
        response.put("hasRisk", snapshot.getRisk() != null);
        response.put("cachedAt", snapshot.getGeneratedAt());
        response.put("ttlSeconds", ttl != null ? ttl.toSeconds() : null);
        response.put("segment", request.getUserSegment());
        return response;
    }

    public Map<String, Object> warmSnapshots(OperationInvocationContext context) {
        WalletWarmRequest request = WalletWarmRequest.from(context);
        Duration ttl = request.getTtlOverride() != null ? request.getTtlOverride() : properties.getDefaultTtl();
        long processed = 0;
        for (int i = 0; i < request.getUserCount(); i++) {
            long userId = request.getStartUserId() + i;
            WalletQueryRequest query = WalletQueryRequest.builder()
                    .userId(userId)
                    .userSegment("warm")
                    .assetCount(40)
                    .includeHistory(false)
                    .historyDays(0)
                    .includeRisk(false)
                    .ttlOverride(ttl)
                    .build();
            buildAndCache(query, ttl);
            processed++;
            if (processed % request.getBatchSize() == 0) {
                Thread.yield();
            }
        }
        return Map.of(
                "status", "WARMED",
                "startUserId", request.getStartUserId(),
                "userCount", processed,
                "ttlSeconds", ttl != null ? ttl.toSeconds() : null
        );
    }

    public Map<String, Object> rebuildLedger(OperationInvocationContext context) {
        WalletRebuildRequest request = WalletRebuildRequest.from(context);
        long totalRecords = 0;
        var batch = new java.util.ArrayList<WalletLedgerRecord>(request.getBatchSize());
        for (int i = 0; i < request.getUserCount(); i++) {
            long userId = request.getStartUserId() + i;
            long userRecords = 0;
            for (int d = 0; d < request.getDaysBack(); d++) {
                WalletLedgerRecord record = processLedgerDay(userId, d);
                totalRecords += record.getRecordCount();
                userRecords += record.getRecordCount();
                batch.add(record);
                if (batch.size() >= request.getBatchSize()) {
                    ledgerRepository.saveLedgerRecords(batch);
                    batch.clear();
                }
            }
            ledgerRepository.upsertSummary(userId, userRecords, request.getDaysBack());
            if ((i + 1) % request.getBatchSize() == 0) {
                Thread.yield();
            }
        }
        if (!batch.isEmpty()) {
            ledgerRepository.saveLedgerRecords(batch);
        }
        return Map.of(
                "status", "REBUILT",
                "userCount", request.getUserCount(),
                "daysBack", request.getDaysBack(),
                "records", totalRecords,
                "retryMode", request.getRetryMode()
        );
    }

    public Map<String, Object> publishBus(OperationInvocationContext context) {
        WalletPublishRequest request = WalletPublishRequest.from(context);
        WalletEvent event = WalletEvent.builder()
                .eventTime(Instant.now())
                .eventType("WALLET_UPDATE")
                .userId(request.getUserId())
                .payload(randomPayload(request.getPayloadSize()))
                .build();
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(request.getTopic(), Long.toString(event.getUserId()), json);
            return Map.of(
                    "status", "PUBLISHED",
                    "topic", request.getTopic(),
                    "userId", event.getUserId(),
                    "payloadBytes", json.length(),
                    "skew", request.getPartitionSkew()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize wallet event", e);
        }
    }

    private WalletSnapshot buildAndCache(WalletQueryRequest request, Duration ttl) {
        WalletSnapshot snapshot = builder.buildSnapshot(request);
        cache.put(snapshot, ttl);
        return snapshot;
    }

    private WalletLedgerRecord processLedgerDay(long userId, int dayOffset) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int operations = random.nextInt(20, 80);
        double checksum = 0;
        for (int i = 0; i < operations; i++) {
            checksum += Math.sin(userId * 0.001 + i) * Math.cos(dayOffset + i * 0.01);
        }
        long recordCount = Math.abs(Math.round(checksum * 100));
        return WalletLedgerRecord.builder()
                .userId(userId)
                .dayOffset(dayOffset)
                .recordCount(recordCount)
                .checksum(checksum)
                .processedAt(Instant.now())
                .build();
    }

    private Map<String, Object> randomPayload(int payloadSize) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("balanceChange", ThreadLocalRandom.current().nextDouble(-5000, 5000));
        payload.put("txnCount", ThreadLocalRandom.current().nextInt(1, 25));
        payload.put("segment", "wallet");
        StringBuilder sb = new StringBuilder(Math.max(0, payloadSize - 64));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < sb.capacity(); i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        payload.put("detail", sb.toString());
        return payload;
    }
}
