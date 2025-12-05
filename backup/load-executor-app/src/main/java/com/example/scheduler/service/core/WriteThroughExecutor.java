package com.example.scheduler.service.core;

import org.springframework.stereotype.Component;

/**
 * Executes write-through plans.
 */
@Component
public class WriteThroughExecutor {

    public <T> int execute(WriteThroughPlan<T> plan) {
        int written = 0;
        for (int i = 0; i < plan.getBatchSize(); i++) {
            T payload = plan.getPayloadSupplier().get();
            plan.getPersistenceWriter().accept(payload);
            if (plan.getCacheWriter() != null) {
                plan.getCacheWriter().accept(payload);
            }
            written++;
        }
        return written;
    }
}
