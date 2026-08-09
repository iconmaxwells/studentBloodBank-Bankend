package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, UUID> {}
