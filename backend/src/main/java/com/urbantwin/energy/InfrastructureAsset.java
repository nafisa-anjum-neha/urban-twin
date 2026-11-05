package com.urbantwin.energy;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Street furniture / utility asset tracked by the platform. */
@Entity
@Table(name = "infrastructure_asset")
@Getter @Setter @NoArgsConstructor
public class InfrastructureAsset {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /** streetlight | water_main | pump | transformer | signal */
    @Column(nullable = false)
    private String assetType;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(nullable = false)
    private int conditionScore;

    @Column(nullable = false)
    private int installedYear;

    /** operational | degraded | failed */
    @Column(nullable = false)
    private String status;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
