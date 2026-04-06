package com.wooya.rebootactuator.actuator;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DrainHealthIndicator implements HealthIndicator {

    private final AtomicBoolean draining = new AtomicBoolean(false);

    public void enableDraining(){
        draining.set(true);
    }

    public void disableDraining(){
        draining.set(false);
    }

    public boolean isDraining(){
        return draining.get();
    }



    @Override
    public @Nullable Health health() {
        if(draining.get()){
            return Health.outOfService()
                            .withDetail("message","Server is draining")
                            .build();
        }
        return Health.up().build();
    }
}
