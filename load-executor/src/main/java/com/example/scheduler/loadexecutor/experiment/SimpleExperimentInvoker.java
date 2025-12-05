package com.example.scheduler.loadexecutor.experiment;

import org.springframework.stereotype.Component;

@Component
public class SimpleExperimentInvoker implements ExperimentInvoker {
    @Override
    public Object invoke(ExperimentOperationHandle handle, OperationInvocationContext context) throws Exception {
        if (handle == null || handle.getInvoker() == null) {
            throw new IllegalArgumentException("Operation handle is not resolvable");
        }
        return handle.getInvoker().invoke(context);
    }
}
