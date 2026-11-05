package com.urbantwin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * UrbanTwin — Smart Urban Infrastructure & Digital Twin Platform.
 *
 * <p>Central API service: ingests sensor telemetry over Kafka, serves the REST
 * contract under /api/v1, exposes the digital-twin live feed, and runs the
 * cross-infrastructure cascade simulator (see docs/SIMULATION.md).</p>
 */
@SpringBootApplication
public class UrbanTwinApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrbanTwinApplication.class, args);
    }
}
