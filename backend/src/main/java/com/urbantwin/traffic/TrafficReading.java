package com.urbantwin.traffic;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** One telemetry sample from a traffic sensor (vehicle count, speed, occupancy). */
@Entity
@Table(name = "traffic_reading", indexes = @Index(name = "idx_traffic_reading_sensor_time", columnList = "sensor_id, recordedAt"))
@Getter @Setter @NoArgsConstructor
public class TrafficReading {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private TrafficSensor sensor;

    @Column(nullable = false)
    private int vehicleCount;

    @Column(nullable = false)
    private double avgSpeedKph;

    /** lane occupancy 0..1 */
    @Column(nullable = false)
    private double occupancyPct;

    /** normalised congestion index 0..1 */
    @Column(nullable = false)
    private double congestionLevel;

    @Column(nullable = false)
    private Instant recordedAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
