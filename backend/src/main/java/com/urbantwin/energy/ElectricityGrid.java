package com.urbantwin.energy;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Electricity distribution grid / substation serving one zone. */
@Entity
@Table(name = "electricity_grid")
@Getter @Setter @NoArgsConstructor
public class ElectricityGrid {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double capacityKw;

    @Column(nullable = false)
    private double lossPct;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
