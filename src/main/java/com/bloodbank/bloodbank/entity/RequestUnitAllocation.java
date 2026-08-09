package com.bloodbank.bloodbank.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_unit_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestUnitAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "blood_unit_id", nullable = false)
    private String bloodUnitId;

    @Column(name = "allocated_at")
    private LocalDateTime allocatedAt;

    @Column(name = "allocated_by")
    private UUID allocatedBy;

    @PrePersist
    protected void onCreate() {
        if (allocatedAt == null) {
            allocatedAt = LocalDateTime.now();
        }
    }
}
