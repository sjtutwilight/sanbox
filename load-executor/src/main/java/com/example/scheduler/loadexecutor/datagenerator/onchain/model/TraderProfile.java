package com.example.scheduler.loadexecutor.datagenerator.onchain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TraderProfile {
    int chainId;
    String accountAddress;
    String label;
    double notionalPreferenceUsd;
    AccountTagSnapshot tagSnapshot;
}
