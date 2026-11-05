package com.urbantwin.emergency;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

/** Emergency response vehicle with station location and dispatch status. */
@Entity
@Table(name = "emergency_vehicle")
@Getter @Setter @NoArgsConstructor
public class EmergencyVehicle {
    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** ambulance | fire_engine | police | rescue */
    @Column(nullable = false)
    private String vehicleType;

    /** available | dispatched | offline */
    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point station;

    /** heading in degrees, updated while en route */
    @Column(nullable = false)
    private double headingDeg;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
