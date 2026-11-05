package com.urbantwin.ingest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Fan-out of a domain event: Kafka `twin.updates` (for the ML service and any other
 * consumers) plus the STOMP topic /topic/twin/updates for the live dashboard.
 * Failures never break the caller — telemetry delivery is best effort.
 */
@Component
public class TwinPublisher {
    private static final Logger log = LoggerFactory.getLogger(TwinPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final SimpMessagingTemplate stomp;

    public TwinPublisher(KafkaTemplate<String, Object> kafka, SimpMessagingTemplate stomp) {
        this.kafka = kafka;
        this.stomp = stomp;
    }

    /** type ∈ TRAFFIC | DRAINAGE | ENERGY | INCIDENT | SIMULATION */
    public void publish(String type, Map<String, Object> zoneMeta, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("zoneId", zoneMeta.get("zoneId"));
        event.put("payload", payload);
        event.put("occurredAt", Instant.now().toString());

        try {
            kafka.send("twin.updates", type, event);
        } catch (Exception e) {
            log.debug("Kafka twin.updates unavailable: {}", e.getMessage());
        }
        try {
            stomp.convertAndSend("/topic/twin/updates", event);
        } catch (Exception e) {
            log.debug("STOMP unavailable: {}", e.getMessage());
        }
    }
}
