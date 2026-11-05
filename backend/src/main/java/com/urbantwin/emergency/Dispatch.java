package com.urbantwin.emergency;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** A dispatch assignment of a vehicle to an incident, with computed route + ETA. */
@Entity
@Table(name = "dispatch")
@Getter @Setter @NoArgsConstructor
public class Dispatch {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private EmergencyIncident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private EmergencyVehicle vehicle;

    @Column(columnDefinition = "text")
    private String routeGeojson;

    @Column(nullable = false)
    private double distanceKm;

    @Column(nullable = false)
    private double etaMin;

    /** dispatched | en_route | arrived | cancelled */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant dispatchedAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
