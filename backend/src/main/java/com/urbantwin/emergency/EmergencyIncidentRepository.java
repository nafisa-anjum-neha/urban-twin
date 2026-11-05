package com.urbantwin.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyIncidentRepository extends JpaRepository<EmergencyIncident, UUID> {
    List<EmergencyIncident> findByStatusOrderBySeverityDesc(String status);

    List<EmergencyIncident> findByIncidentType(String type);

    long countByStatus(String status);

    Optional<EmergencyIncident> findTopByStatusNotOrderBySeverityDesc(String excluded);
}
