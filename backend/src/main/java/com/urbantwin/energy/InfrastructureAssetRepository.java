package com.urbantwin.energy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface InfrastructureAssetRepository extends JpaRepository<InfrastructureAsset, UUID> {
    List<InfrastructureAsset> findByZoneId(UUID zoneId);

    List<InfrastructureAsset> findByAssetType(String assetType);

    List<InfrastructureAsset> findByStatus(String status);

    @Query("select a from InfrastructureAsset a where (:type is null or a.assetType = :type) " +
           "and (:status is null or a.status = :status) order by a.conditionScore asc")
    List<InfrastructureAsset> search(@Param("type") String type, @Param("status") String status);
}
