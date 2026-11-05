package com.urbantwin.maintenance;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import com.urbantwin.roads.Road;
import com.urbantwin.energy.InfrastructureAsset;
import java.time.Instant;
import java.util.UUID;

/** Repair/work order for a road segment or infrastructure asset. */
@Entity
@Table(name = "maintenance_request")
@Getter @Setter @NoArgsConstructor
public class MaintenanceRequest {
    public enum Status { open, assigned, in_progress, resolved, closed }
    public enum Priority { p1, p2, p3, p4 }

    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private InfrastructureAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "road_id")
    private Road road;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** p1..p4 */
    @Column(nullable = false)
    private String priority;

    /** open | assigned | in_progress | resolved | closed */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String reportedBy;

    private Instant scheduledDate;

    private Instant resolvedAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
