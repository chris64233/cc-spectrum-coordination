package com.chris64233.spectrumcoordination;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpectrumCoordinationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpectrumCoordinationApplication.class, args);
    }
}
