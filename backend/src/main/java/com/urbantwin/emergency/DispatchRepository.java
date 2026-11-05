package com.urbantwin.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {
    Optional<Dispatch> findTopByIncidentOrderByDispatchedAtDesc(EmergencyIncident incident);

    long countByStatus(String status);
}
