package com.wooya.rebootactuator.service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RunAsyncService {

    private static final Logger log = LoggerFactory.getLogger(RunAsyncService.class);

    @Async
    public CompletableFuture<Void> runAsync(int num) {
        for (int count = 1; count <= num; count++) {
            try {
                TimeUnit.SECONDS.sleep(3);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("runAsync interrupted. num={}", num, e);
                return CompletableFuture.completedFuture(null);
            }

            log.info(
                    "runAsync threadName={}, currentTime={}, count={}",
                    Thread.currentThread().getName(),
                    LocalDateTime.now(),
                    count
            );
        }

        return CompletableFuture.completedFuture(null);
    }
}
