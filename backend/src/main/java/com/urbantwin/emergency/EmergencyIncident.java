package com.urbantwin.emergency;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Emergency incident reported to city command centre. */
@Entity
@Table(name = "emergency_incident")
@Getter @Setter @NoArgsConstructor
public class EmergencyIncident {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private String name;

    /** medical | fire | crime | hazard | flood */
    @Column(nullable = false)
    private String incidentType;

    /** 1..5 */
    @Column(nullable = false)
    private int severity;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(columnDefinition = "text")
    private String description;

    /** active | dispatched | resolved */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant reportedAt;

    private Instant resolvedAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
