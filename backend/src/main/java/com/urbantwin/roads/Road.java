package com.urbantwin.roads;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.LineString;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Road segment with condition score and geometry (geography LineString). */
@Entity
@Table(name = "road")
@Getter @Setter @NoArgsConstructor
public class Road {
    public enum RoadClass { hw, ar, co, lo }

    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private String name;

    /** hw | ar | co | lo */
    @Column(nullable = false)
    private String roadClass;

    /** 0 (failed) .. 100 (new) */
    @Column(nullable = false)
    private int conditionScore;

    @Column(nullable = false)
    private int lanes;

    @Column(nullable = false)
    private int speedLimitKph;

    @Column(columnDefinition = "geography(LineString,4326)")
    private LineString path;

    @Column(columnDefinition = "text")
    private String geojson;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
