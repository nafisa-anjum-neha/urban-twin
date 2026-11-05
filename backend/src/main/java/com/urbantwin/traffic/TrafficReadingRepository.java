package com.urbantwin.traffic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrafficReadingRepository extends JpaRepository<TrafficReading, UUID> {
    List<TrafficReading> findBySensorIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID sensorId, Instant from, Instant to);

    Optional<TrafficReading> findFirstBySensorIdOrderByRecordedAtDesc(UUID sensorId);

    long countByRecordedAtBefore(Instant cut);

    void deleteByRecordedAtBefore(Instant cut);

    /** average congestion across all sensors in a time window (city firehose). */
    @Query("select coalesce(avg(r.congestionLevel), 0) from TrafficReading r where r.recordedAt between :from and :to")
    double avgCongestionBetween(@Param("from") Instant from, @Param("to") Instant to);
}
