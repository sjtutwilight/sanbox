package com.example.scheduler.loadexecutor.datagenerator.onchain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class AccountBasicMetadata {
    int chainId;
    String accountAddress;
    boolean contract;
    boolean router;
    boolean dexContract;
    boolean cexAddress;
    long firstSeenBlock;
    Instant firstSeenTime;
    String label;
}
