package com.example.scheduler.loadexecutor.experiment;

@FunctionalInterface
public interface ExperimentOperationInvoker {
    Object invoke(OperationInvocationContext context) throws Exception;
}
