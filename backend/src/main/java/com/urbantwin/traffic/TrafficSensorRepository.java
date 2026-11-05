package com.urbantwin.traffic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TrafficSensorRepository extends JpaRepository<TrafficSensor, UUID> {
    List<TrafficSensor> findByStatus(String status);

    List<TrafficSensor> findByRoadZoneId(UUID zoneId);
}
