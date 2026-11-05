package com.urbantwin.drainage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaterLevelReadingRepository extends JpaRepository<WaterLevelReading, UUID> {
    List<WaterLevelReading> findBySensorIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID sensorId, Instant from, Instant to);

    Optional<WaterLevelReading> findTopBySensorOrderByRecordedAtDesc(DrainageSensor sensor);

    @Query("select r from WaterLevelReading r where r.recordedAt between :from and :to order by r.waterLevelCm desc")
    List<WaterLevelReading> latestByWindow(@Param("from") Instant from, @Param("to") Instant to);
}
