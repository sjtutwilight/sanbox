package com.example.scheduler.loadexecutor.datagenerator.onchain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class TokenMetadata {
    int chainId;
    String tokenAddress;
    String symbol;
    String name;
    int decimals;
    String category;
    boolean stablecoin;
    boolean bluechip;
    long createdBlock;
    Instant createdTime;
    String extraMetaJson;
    double basePriceUsd;
    double baseMcapUsd;
}
