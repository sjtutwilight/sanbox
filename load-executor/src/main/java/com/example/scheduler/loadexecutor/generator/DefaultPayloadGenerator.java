package com.example.scheduler.loadexecutor.generator;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.HotKeyConfig;
import com.example.scheduler.loadexecutor.domain.LoadPhase;
import com.example.scheduler.loadexecutor.generator.template.PayloadTemplateRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class DefaultPayloadGenerator implements RequestPayloadGenerator {

    private final PayloadTemplateRegistry templateRegistry;

    @Override
    public Map<String, Object> nextPayload(Command command, LoadPhase phase, long sequence) {
        Map<String, Object> payload = new HashMap<>();
        templateRegistry.produce(command, phase, sequence).ifPresent(payload::putAll);
        if (payload.isEmpty() && command.getDataRequest() != null) {
            payload.putAll(command.getDataRequest());
        }
        payload.put("sequence", sequence);
        payload.put("experimentRunId", command.getExperimentRunId());
        HotKeyConfig hotKeyConfig = phase.getHotKeyConfig();
        if (hotKeyConfig != null && hotKeyConfig.getHotKeyCount() > 0 && hotKeyConfig.getKeySpaceSize() > 0) {
            payload.put("key", nextKey(hotKeyConfig));
        }
        return payload;
    }

    private String nextKey(HotKeyConfig config) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean hitHotKey = random.nextDouble() < config.getClampedRatio();
        long keyIndex;
        if (hitHotKey) {
            keyIndex = random.nextInt(config.getHotKeyCount());
        } else {
            long coldSpace = Math.max(1, config.getKeySpaceSize() - config.getHotKeyCount());
            keyIndex = config.getHotKeyCount() + random.nextLong(coldSpace);
        }
        String prefix = config.getKeyPrefix() != null ? config.getKeyPrefix() : "key";
        return prefix + ":" + keyIndex;
    }
}
