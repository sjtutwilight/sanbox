package com.example.scheduler.loadexecutor.experiment.wallet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class WalletSnapshotBuilder {

    private static final String[] SYMBOLS = {
            "BTC", "ETH", "BNB", "SOL", "ADA", "DOGE", "XRP", "ARB", "OP", "AVAX",
            "LTC", "LINK", "MATIC", "NEO", "SUI", "ICP", "TON", "FIL", "DOT", "ATOM"
    };

    public WalletSnapshot buildSnapshot(WalletQueryRequest request) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int assetCount = Math.max(1, request.getAssetCount());
        List<WalletSnapshot.AssetPosition> assets = new ArrayList<>(assetCount);
        for (int i = 0; i < assetCount; i++) {
            String asset = SYMBOLS[(i + request.segmentOffset()) % SYMBOLS.length];
            double free = randomAmount(random, asset);
            double locked = free * random.nextDouble(0.05, 0.2);
            double btcValue = convertToBtc(asset, free + locked);
            double usdValue = btcValue * 68000 + random.nextDouble(-50, 50);
            assets.add(WalletSnapshot.AssetPosition.builder()
                    .asset(asset)
                    .free(free)
                    .locked(locked)
                    .btcValue(round(btcValue))
                    .usdValue(round(usdValue))
                    .build());
        }

        List<WalletSnapshot.TransactionRecord> history = request.isIncludeHistory()
                ? buildHistory(random, request.getHistoryDays(), request.getAssetCount())
                : List.of();
        WalletSnapshot.RiskAssessment risk = request.isIncludeRisk()
                ? buildRisk(random, request)
                : null;
        byte[] filler = buildFiller(random, request.getFillerBytes());

        return WalletSnapshot.builder()
                .userId(request.getUserId())
                .assets(List.copyOf(assets))
                .history(List.copyOf(history))
                .risk(risk)
                .generatedAt(Instant.now())
                .filler(filler)
                .build();
    }

    private List<WalletSnapshot.TransactionRecord> buildHistory(ThreadLocalRandom random, int days, int assetCount) {
        int recordCount = Math.min(200, Math.max(10, days * 6));
        List<WalletSnapshot.TransactionRecord> records = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            long secondsAgo = random.nextInt(days * 24 * 3600 + 1);
            String asset = SYMBOLS[(i + assetCount) % SYMBOLS.length];
            double amount = randomAmount(random, asset) / 10;
            double usdValue = convertToBtc(asset, amount) * 68000;
            records.add(WalletSnapshot.TransactionRecord.builder()
                    .time(Instant.now().minusSeconds(secondsAgo))
                    .type(i % 2 == 0 ? "TRADE" : "TRANSFER")
                    .asset(asset)
                    .amount(round(amount))
                    .usdValue(round(usdValue))
                    .build());
        }
        return records;
    }

    private WalletSnapshot.RiskAssessment buildRisk(ThreadLocalRandom random, WalletQueryRequest request) {
        double marginLevel = random.nextDouble(1.1, 4.5);
        double maxBorrow = random.nextDouble(1000, 150000);
        double liq = random.nextDouble(15000, 45000);
        List<String> alerts = new ArrayList<>();
        if (marginLevel < 1.3) {
            alerts.add("MARGIN_CALL");
        }
        if ("vip".equalsIgnoreCase(request.getUserSegment()) && maxBorrow > 120000) {
            alerts.add("VIP_OVERRUN");
        }
        return WalletSnapshot.RiskAssessment.builder()
                .marginLevel(round(marginLevel))
                .maxBorrow(round(maxBorrow))
                .liquidationPrice(round(liq))
                .alerts(alerts)
                .build();
    }

    private double randomAmount(ThreadLocalRandom random, String asset) {
        double base = switch (asset.toUpperCase(Locale.ROOT)) {
            case "BTC" -> 1.5;
            case "ETH" -> 25;
            case "BNB" -> 60;
            case "SOL" -> 320;
            case "USDT" -> 10000;
            default -> 500;
        };
        return round(base * random.nextDouble(0.5, 1.5));
    }

    private double convertToBtc(String asset, double amount) {
        return switch (asset.toUpperCase(Locale.ROOT)) {
            case "BTC" -> amount;
            case "ETH" -> amount / 15.0;
            case "BNB" -> amount / 120.0;
            case "SOL" -> amount / 250.0;
            case "USDT" -> amount / 68000.0;
            default -> amount / 5000.0;
        };
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private byte[] buildFiller(ThreadLocalRandom random, int bytes) {
        if (bytes <= 0) {
            return null;
        }
        byte[] blob = new byte[bytes];
        random.nextBytes(blob);
        return blob;
    }
}
