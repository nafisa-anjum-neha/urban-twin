package com.urbantwin.energy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GridReadingRepository extends JpaRepository<GridReading, UUID> {
    List<GridReading> findByGridIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID gridId, Instant from, Instant to);

    Optional<GridReading> findTopByGridOrderByRecordedAtDesc(ElectricityGrid grid);

    @Query("select count(r) from GridReading r where r.grid.id = :gridId and r.outageReported = true " +
           "and r.recordedAt between :from and :to")
    long countOutages(@Param("gridId") UUID gridId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(r.consumptionKw), 0) from GridReading r where r.grid.id = :gridId " +
           "and r.recordedAt between :from and :to")
    double sumConsumption(@Param("gridId") UUID gridId, @Param("from") Instant from, @Param("to") Instant to);
}
