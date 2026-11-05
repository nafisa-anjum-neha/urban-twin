package com.urbantwin.ingest;

import com.urbantwin.traffic.TrafficDtos.TrafficPayload;
import com.urbantwin.traffic.TrafficService;
import com.urbantwin.energy.EnergyDtos.EnergyPayload;
import com.urbantwin.energy.EnergyService;
import com.urbantwin.drainage.DrainageDtos.WaterPayload;
import com.urbantwin.drainage.DrainageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Telemetry ingestion from the Kafka topics (contract §5). */
@Component
public class KafkaIngestListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaIngestListener.class);

    private final TrafficService traffic;
    private final DrainageService drainage;
    private final EnergyService energy;

    public KafkaIngestListener(TrafficService traffic, DrainageService drainage, EnergyService energy) {
        this.traffic = traffic;
        this.drainage = drainage;
        this.energy = energy;
    }

    @KafkaListener(id = "traffic-ingest", topics = "traffic.readings")
    public void traffic(TrafficPayload payload) {
        try {
            traffic.record(payload);
        } catch (Exception e) {
            log.warn("traffic.readings failed for {}: {}", payload, e.getMessage());
        }
    }

    @KafkaListener(id = "drainage-ingest", topics = "drainage.readings")
    public void drainage(WaterPayload payload) {
        try {
            drainage.record(payload);
        } catch (Exception e) {
            log.warn("drainage.readings failed for {}: {}", payload, e.getMessage());
        }
    }

    @KafkaListener(id = "energy-ingest", topics = "energy.readings")
    public void energy(EnergyPayload payload) {
        try {
            energy.record(payload);
        } catch (Exception e) {
            log.warn("energy.readings failed for {}: {}", payload, e.getMessage());
        }
    }
}
