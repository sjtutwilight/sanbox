package com.example.scheduler.loadexecutor.domain;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class LoadPlan {

    String experimentRunId;
    @Singular
    List<LoadPhase> phases;

    public Optional<LoadPhase> phaseAt(Duration elapsed) {
        return phases.stream()
                .sorted(Comparator.comparing(LoadPhase::getStartOffset))
                .filter(phase -> phase.contains(elapsed))
                .findFirst();
    }

    public Duration totalDuration() {
        return phases.stream()
                .map(LoadPhase::getEndOffset)
                .filter(duration -> duration != null)
                .max(Duration::compareTo)
                .orElse(null);
    }

    public int maxConcurrency() {
        return phases.stream()
                .mapToInt(LoadPhase::getMaxConcurrency)
                .max()
                .orElse(0);
    }
}
