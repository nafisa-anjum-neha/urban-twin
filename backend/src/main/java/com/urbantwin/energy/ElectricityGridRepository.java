package com.urbantwin.energy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ElectricityGridRepository extends JpaRepository<ElectricityGrid, UUID> {
    List<ElectricityGrid> findByZoneId(UUID zoneId);
}
