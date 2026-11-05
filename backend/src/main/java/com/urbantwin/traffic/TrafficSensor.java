package com.urbantwin.traffic;

import jakarta.persistence.*;
import lombok.*;
import com.urbantwin.roads.Road;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

/** Roadside traffic detection sensor (inductive loop / camera). */
@Entity
@Table(name = "traffic_sensor")
@Getter @Setter @NoArgsConstructor
public class TrafficSensor {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "road_id")
    private Road road;

    @Column(nullable = false)
    private String name;

    /** active | inactive | faulty */
    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
