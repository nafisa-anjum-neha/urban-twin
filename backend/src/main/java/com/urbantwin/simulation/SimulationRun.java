package com.urbantwin.simulation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** Persisted cascade simulation run with its full result graph (JSONB). */
@Entity
@Table(name = "simulation_run")
@Getter @Setter @NoArgsConstructor
public class SimulationRun {
    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** rainfall | substation_outage | water_main_burst | sensor_failure */
    @Column(nullable = false)
    private String scenario;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String inputParamsJson;

    @Column(columnDefinition = "jsonb")
    private String resultJson;

    /** running | completed | failed */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
