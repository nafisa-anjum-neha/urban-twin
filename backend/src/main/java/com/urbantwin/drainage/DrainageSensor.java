package com.urbantwin.drainage;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

/** Water-level gauge installed in a manhole/culvert of the drainage network. */
@Entity
@Table(name = "drainage_sensor")
@Getter @Setter @NoArgsConstructor
public class DrainageSensor {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false)
    private DrainageNetwork network;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    /** normal | warning | critical | faulty */
    @Column(nullable = false)
    private String status;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
