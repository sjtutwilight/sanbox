package com.example.scheduler.loadexecutor.generator;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.LoadPhase;

import java.util.Map;

public interface RequestPayloadGenerator {

    Map<String, Object> nextPayload(Command command, LoadPhase phase, long sequence);
}
