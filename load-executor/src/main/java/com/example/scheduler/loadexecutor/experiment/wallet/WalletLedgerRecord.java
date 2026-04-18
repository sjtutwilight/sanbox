package com.example.scheduler.loadexecutor.experiment.wallet;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class WalletLedgerRecord {
    long userId;
    int dayOffset;
    long recordCount;
    double checksum;
    Instant processedAt;
}
