package com.wooya.rebootactuator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RebootActuatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RebootActuatorApplication.class, args);
    }

}
