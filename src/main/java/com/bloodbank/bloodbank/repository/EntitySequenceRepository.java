package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.EntitySequence;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EntitySequenceRepository extends JpaRepository<EntitySequence, EntityType> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EntitySequence s WHERE s.entityType = :entityType")
    Optional<EntitySequence> findForUpdate(@Param("entityType") EntityType entityType);
}
