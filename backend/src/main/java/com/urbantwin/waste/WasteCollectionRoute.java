package com.urbantwin.waste;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Optimised waste collection route (stops stored in visiting order). */
@Entity
@Table(name = "waste_collection_route")
@Getter @Setter @NoArgsConstructor
public class WasteCollectionRoute {
    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private String vehicleId;

    /** ordered stop list as JSON: [{binId, order, lat, lon, fillLevel}] */
    @Column(columnDefinition = "jsonb")
    private String stopsJson;

    @Column(nullable = false)
    private double totalDistanceKm;

    @Column(nullable = false)
    private double estimatedDurationMin;

    /** planned | active | completed */
    @Column(nullable = false)
    private String status;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
