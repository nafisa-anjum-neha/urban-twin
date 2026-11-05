package com.urbantwin.energy;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** Telemetry from a grid meter: consumption, voltage and outage flags. */
@Entity
@Table(name = "grid_reading", indexes = @Index(name = "idx_grid_reading_time", columnList = "grid_id, recordedAt"))
@Getter @Setter @NoArgsConstructor
public class GridReading {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grid_id", nullable = false)
    private ElectricityGrid grid;

    @Column(nullable = false)
    private double consumptionKw;

    @Column(nullable = false)
    private double voltage;

    @Column(nullable = false)
    private boolean outageReported;

    @Column(nullable = false)
    private Instant recordedAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
