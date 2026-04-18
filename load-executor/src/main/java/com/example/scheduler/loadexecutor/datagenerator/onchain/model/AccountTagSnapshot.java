package com.example.scheduler.loadexecutor.datagenerator.onchain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class AccountTagSnapshot {
    int chainId;
    String accountAddress;
    boolean whale;
    boolean smart;
    boolean bot;
    boolean cexDeposit;
    int vipLevel;
    String segment;
    Instant updatedAt;
}
