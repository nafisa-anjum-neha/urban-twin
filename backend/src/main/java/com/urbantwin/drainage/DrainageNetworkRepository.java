package com.urbantwin.drainage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DrainageNetworkRepository extends JpaRepository<DrainageNetwork, UUID> {
    java.util.List<DrainageNetwork> findByZoneId(UUID zoneId);
}
