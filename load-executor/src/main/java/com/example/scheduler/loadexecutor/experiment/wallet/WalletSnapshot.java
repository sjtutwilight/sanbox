package com.example.scheduler.loadexecutor.experiment.wallet;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class WalletSnapshot {
    long userId;
    @Singular("asset")
    List<AssetPosition> assets;
    @Singular("transaction")
    List<TransactionRecord> history;
    RiskAssessment risk;
    Instant generatedAt;

    public List<AssetPosition> getAssets() {
        return assets == null ? Collections.emptyList() : assets;
    }

    public List<TransactionRecord> getHistory() {
        return history == null ? Collections.emptyList() : history;
    }

    @Value
    @Builder
    public static class AssetPosition {
        String asset;
        double free;
        double locked;
        double btcValue;
        double usdValue;
    }

    @Value
    @Builder
    public static class TransactionRecord {
        Instant time;
        String type;
        String asset;
        double amount;
        double usdValue;
    }

    @Value
    @Builder
    public static class RiskAssessment {
        double marginLevel;
        double maxBorrow;
        double liquidationPrice;
        List<String> alerts;
    }
}
