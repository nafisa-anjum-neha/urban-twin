package com.urbantwin.waste;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WasteBinRepository extends JpaRepository<WasteBin, UUID> {
    List<WasteBin> findByZoneId(UUID zoneId);

    List<WasteBin> findByZoneIdAndFillLevelPctGreaterThanEqual(UUID zoneId, double minFill);

    List<WasteBin> findByFillLevelPctGreaterThanEqual(double minFill);
}
