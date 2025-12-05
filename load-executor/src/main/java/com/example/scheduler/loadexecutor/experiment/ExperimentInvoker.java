package com.example.scheduler.loadexecutor.experiment;

public interface ExperimentInvoker {
    Object invoke(ExperimentOperationHandle handle, OperationInvocationContext context) throws Exception;
}
