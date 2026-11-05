package com.urbantwin.drainage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DrainageSensorRepository extends JpaRepository<DrainageSensor, UUID> {
    List<DrainageSensor> findByNetworkZoneId(UUID zoneId);

    List<DrainageSensor> findByStatus(String status);
}
