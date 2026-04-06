package com.wooya.rebootactuator.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wooya.rebootactuator.service.RunService;

@RestController
@RequestMapping("/v1/api")
public class RunController {

    private final RunService runService;

    public RunController(RunService runService) {
        this.runService = runService;
    }

    @GetMapping("/getRun/{num}")
    public CompletableFuture<ResponseEntity<String>> getRun(@PathVariable int num) {
        return runService.getRunResponse(num)
                .thenApply(ResponseEntity::ok);
    }
}
