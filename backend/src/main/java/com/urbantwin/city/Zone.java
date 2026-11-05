package com.urbantwin.city;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Polygon;
import java.time.Instant;
import java.util.UUID;

/** Administrative zone within a city; polygon boundary stored as geography(Polygon,4326). */
@Entity
@Table(name = "zone")
@Getter @Setter @NoArgsConstructor
public class Zone {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false)
    private String name;

    /** residential | commercial | industrial | mixed */
    @Column(nullable = false)
    private String zoneType;

    @Column(columnDefinition = "geography(Polygon,4326)")
    private Polygon boundary;

    @Column(columnDefinition = "text")
    private String geojson;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
