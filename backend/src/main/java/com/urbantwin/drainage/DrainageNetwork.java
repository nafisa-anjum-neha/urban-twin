package com.urbantwin.drainage;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import com.urbantwin.city.Zone;
import java.time.Instant;
import java.util.UUID;

/** Storm-water drainage network (primary trunk or secondary line). */
@Entity
@Table(name = "drainage_network")
@Getter @Setter @NoArgsConstructor
public class DrainageNetwork {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacityM3PerHour;

    /** primary | secondary */
    @Column(nullable = false)
    private String networkClass;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
