package com.example.scheduler.loadexecutor.datagenerator.onchain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class DexPoolMetadata {
    int chainId;
    String dexName;
    String dexVersion;
    String poolAddress;
    String token0Address;
    String token1Address;
    int feeTierBps;
    long createdBlock;
    Instant createdTime;
    boolean active;
    String routerAddress;
}
