package com.example.scheduler.service.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Executes cache-aside flows defined by {@link CacheAsidePlan}.
 */
@Component
@Slf4j
public class CacheAsideExecutor {

    public <T> List<T> execute(CacheAsidePlan<T> plan) {
        List<T> cached = safeInvoke(plan.getCacheReader());
        if (!cached.isEmpty()) {
            return cached;
        }
        List<T> loaded = safeInvoke(plan.getSourceLoader());
        if (loaded.isEmpty()) {
            if (plan.getEmptyValueWriter() != null) {
                plan.getEmptyValueWriter().run();
            }
            return Collections.emptyList();
        }
        if (plan.getCacheWriter() != null) {
            try {
                plan.getCacheWriter().accept(loaded);
            } catch (Exception e) {
                log.warn("cache writer failed", e);
            }
        }
        return safeInvoke(plan.getCacheReader());
    }

    private <T> List<T> safeInvoke(java.util.function.Supplier<List<T>> supplier) {
        if (supplier == null) {
            return Collections.emptyList();
        }
        try {
            List<T> result = supplier.get();
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.warn("cache-aside supplier failed", e);
            return Collections.emptyList();
        }
    }
}
