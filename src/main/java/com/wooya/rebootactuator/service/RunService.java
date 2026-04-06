package com.wooya.rebootactuator.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class RunService {

    private final TaskExecutor taskExecutor;
    private final RunAsyncService runAsyncService;

    public RunService(
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            RunAsyncService runAsyncService
    ) {
        this.taskExecutor = taskExecutor;
        this.runAsyncService = runAsyncService;
    }

    public CompletableFuture<String> getRunResponse(int num) {
        runAsyncService.runAsync(num);

        return CompletableFuture.supplyAsync(
                () -> "OK",
                CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS, taskExecutor)
        );
    }
}
