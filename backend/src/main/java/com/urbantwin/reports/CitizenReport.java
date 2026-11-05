package com.urbantwin.reports;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Citizen-reported infrastructure issue (crowd-sourced ground truth). */
@Entity
@Table(name = "citizen_report")
@Getter @Setter @NoArgsConstructor
public class CitizenReport {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /** pothole | leak | lighting | garbage | flooding | other */
    @Column(nullable = false)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    /** submitted | acknowledged | in_progress | resolved */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int upvotes;

    @Column(nullable = false)
    private String reporterName;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
