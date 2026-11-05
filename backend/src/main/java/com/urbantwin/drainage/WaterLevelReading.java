package com.urbantwin.drainage;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** Water level / flow telemetry from a drainage sensor. */
@Entity
@Table(name = "water_level_reading", indexes = @Index(name = "idx_water_reading_sensor_time", columnList = "sensor_id, recordedAt"))
@Getter @Setter @NoArgsConstructor
public class WaterLevelReading {
    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private DrainageSensor sensor;

    @Column(nullable = false)
    private double waterLevelCm;

    @Column(nullable = false)
    private double flowRateLps;

    /** rainfall (mm/h) observed at the sensor at sample time */
    @Column(nullable = false)
    private double rainfallMm;

    /** low | moderate | high | severe */
    @Column(nullable = false)
    private String riskLevel;

    @Column(nullable = false)
    private Instant recordedAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
