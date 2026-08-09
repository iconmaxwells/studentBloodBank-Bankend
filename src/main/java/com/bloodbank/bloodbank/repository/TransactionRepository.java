package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Transaction;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Page<Transaction> findByType(TransactionType type, Pageable pageable);
}
