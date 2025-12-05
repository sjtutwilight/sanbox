package com.example.scheduler.loadexecutor.generator.template;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.LoadPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PayloadTemplateRegistry {

    private final List<ExperimentPayloadTemplate> templates;

    public Optional<Map<String, Object>> produce(Command command, LoadPhase phase, long sequence) {
        return templates.stream()
                .filter(template -> template.supports(command))
                .findFirst()
                .map(template -> template.produce(command, phase, sequence));
    }
}
