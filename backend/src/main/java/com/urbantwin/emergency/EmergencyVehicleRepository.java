package com.urbantwin.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmergencyVehicleRepository extends JpaRepository<EmergencyVehicle, UUID> {
    List<EmergencyVehicle> findByStatus(String status);
}
