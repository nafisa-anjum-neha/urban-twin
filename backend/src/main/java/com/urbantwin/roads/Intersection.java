package com.urbantwin.roads;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Signalised/plain intersection node of the road network. */
@Entity
@Table(name = "intersection")
@Getter @Setter @NoArgsConstructor
public class Intersection {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(nullable = false)
    private boolean trafficLight;

    @Column(nullable = false)
    private int signalCycleSeconds;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
