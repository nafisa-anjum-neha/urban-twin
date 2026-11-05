package com.urbantwin.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, UUID> {
    List<MaintenanceRequest> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    List<MaintenanceRequest> findByRoadId(UUID roadId);

    List<MaintenanceRequest> findByAssetId(UUID assetId);

    List<MaintenanceRequest> findAllByOrderByCreatedAtDesc();
}
