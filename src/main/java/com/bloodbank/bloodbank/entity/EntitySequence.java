package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "entity_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntitySequence {
    @Id
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(nullable = false)
    private Long lastValue;
}
