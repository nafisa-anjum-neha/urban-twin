package com.urbantwin.waste;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WasteCollectionRouteRepository extends JpaRepository<WasteCollectionRoute, UUID> {
    List<WasteCollectionRoute> findByStatus(String status);

    List<WasteCollectionRoute> findAllByOrderByCreatedAtDesc();
}
