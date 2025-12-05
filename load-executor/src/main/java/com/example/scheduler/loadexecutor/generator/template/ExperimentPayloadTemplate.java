package com.example.scheduler.loadexecutor.generator.template;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.LoadPhase;

import java.util.Map;

public interface ExperimentPayloadTemplate {

    boolean supports(Command command);

    Map<String, Object> produce(Command command, LoadPhase phase, long sequence);
}
