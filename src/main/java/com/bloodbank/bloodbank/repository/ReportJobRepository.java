package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {}
