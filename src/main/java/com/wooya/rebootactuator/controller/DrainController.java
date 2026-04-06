package com.wooya.rebootactuator.controller;

import com.wooya.rebootactuator.actuator.DrainHealthIndicator;
import com.wooya.rebootactuator.actuator.ReadinessManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drain")
public class DrainController {

    private final DrainHealthIndicator drainHealthIndicator;

    private final ReadinessManager readinessManager;

    public DrainController(DrainHealthIndicator drainHealthIndicator
    , ReadinessManager readinessManager) {
        this.drainHealthIndicator = drainHealthIndicator;
        this.readinessManager = readinessManager;
    }

    @PostMapping("/on")
    public ResponseEntity<String> drain(){
        drainHealthIndicator.enableDraining();
        readinessManager.markAcceptingTraffic();
        return ResponseEntity.ok("Draining");
    }

    @PostMapping("/off")
    public ResponseEntity<String> drainOff(){
        drainHealthIndicator.disableDraining();
        readinessManager.markRefusingTraffic();
        return ResponseEntity.ok("Drain Off");
    }
}
