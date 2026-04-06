package com.wooya.rebootactuator.actuator;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ReadinessManager {

    private final ApplicationContext applicationContext;

    public ReadinessManager(ApplicationContext applicationContext){
        this.applicationContext = applicationContext;
    }

    public void markRefusingTraffic(){
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
    }

    public void markAcceptingTraffic(){
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
    }

}
