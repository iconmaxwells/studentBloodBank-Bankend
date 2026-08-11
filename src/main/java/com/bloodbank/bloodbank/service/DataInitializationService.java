package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.SupplyRequestStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Urgency;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.UnitStatus;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.util.BloodBankUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final StaffRoleRepository staffRoleRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final DonorRepository donorRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bloodBankRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final DonorRewardRepository donorRewardRepository;
    private final BloodUnitRepository bloodUnitRepository;
    private final SupplyRequestRepository supplyRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final DisplayCodeService displayCodeService;
    private final DonorService donorService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting database initialization...");
        initializePermissions();
        initializeRoles();
        initializeStaffRoles();
        syncStaffRolePermissions();
        initializeSystemSettings();
        initializeBloodBanks();
        initializeDemoUsers();
        int backfilled = donorService.backfillMissingBloodGroups();
        if (backfilled > 0) {
            log.info("Backfilled blood group for {} donor(s)", backfilled);
        }
        log.info("Database initialization completed!");
    }

    private void initializePermissions() {
        String[] permissionNames = {
                "canApproveRequests", "canRejectRequests", "canManageInventory",
                "canManageDonors", "canConductWithdrawals", "canViewReports", "canManageStaff"
        };
        for (String name : permissionNames) {
            if (permissionRepository.findByName(name).isEmpty()) {
                permissionRepository.save(Permission.builder().name(name).description(name).active(true).build());
            }
        }
    }

    private void initializeRoles() {
        String[][] roleData = {
                {"admin", "System administrator"}, {"staff", "Blood bank staff"},
                {"donor", "Blood donors"}, {"hospital", "Hospital institutions"},
                {"specialist", "Medical screening specialist"}
        };
        for (String[] info : roleData) {
            if (roleRepository.findByName(info[0]).isEmpty()) {
                roleRepository.save(Role.builder().name(info[0]).description(info[1]).active(true).build());
            }
        }
    }

    private Map<String, List<String>> staffRolePermissionMatrix() {
        return Map.of(
                "Admin", List.of("canApproveRequests", "canRejectRequests", "canManageInventory", "canManageDonors", "canConductWithdrawals", "canViewReports", "canManageStaff"),
                "Senior Staff", List.of("canApproveRequests", "canRejectRequests", "canManageInventory", "canManageDonors", "canConductWithdrawals", "canViewReports"),
                "Junior Staff", List.of("canApproveRequests", "canRejectRequests", "canManageInventory", "canManageDonors", "canConductWithdrawals"),
                "Technician", List.of("canApproveRequests", "canRejectRequests", "canConductWithdrawals")
        );
    }

    private void initializeStaffRoles() {
        for (var entry : staffRolePermissionMatrix().entrySet()) {
            if (staffRoleRepository.findByName(entry.getKey()).isEmpty()) {
                Set<Permission> perms = new HashSet<>();
                for (String p : entry.getValue()) {
                    permissionRepository.findByName(p).ifPresent(perms::add);
                }
                staffRoleRepository.save(StaffRole.builder().name(entry.getKey()).description(entry.getKey()).permissions(perms).active(true).build());
            }
        }
    }

    private void syncStaffRolePermissions() {
        for (var entry : staffRolePermissionMatrix().entrySet()) {
            staffRoleRepository.findByName(entry.getKey()).ifPresent(role -> {
                Set<Permission> perms = new HashSet<>();
                for (String p : entry.getValue()) {
                    permissionRepository.findByName(p).ifPresent(perms::add);
                }
                role.setPermissions(perms);
                staffRoleRepository.save(role);
            });
        }
        log.info("Synchronized staff role permissions");
    }

    private void initializeSystemSettings() {
        if (systemSettingsRepository.count() == 0) {
            systemSettingsRepository.save(SystemSettings.builder()
                    .bloodBankName("National Blood Bank")
                    .contactEmail("contact@bloodbank.com")
                    .contactPhone("+233000000000")
                    .address("Accra, Ghana")
                    .donorCompensationDefault(75.0)
                    .hospitalServiceChargeDefault(500.0)
                    .minDonationIntervalDays(90)
                    .minAge(18)
                    .maxAge(65)
                    .minWeightKg(50.0)
                    .build());
        }
    }

    private void initializeBloodBanks() {
        if (bloodBankRepository.count() == 0) {
            bloodBankRepository.save(BloodBank.builder()
                    .displayCode("BB001")
                    .name("Main Collection Center")
                    .address("123 Blood Bank Road")
                    .location("Accra")
                    .latitude(5.6037)
                    .longitude(-0.1870)
                    .phone("+233201234567")
                    .email("main@bloodbank.com")
                    .operatingHours("Mon-Sat 8:00-18:00")
                    .availableServices(List.of("Donation", "Testing", "Storage", "Emergency"))
                    .build());
            bloodBankRepository.save(BloodBank.builder()
                    .displayCode("BB002")
                    .name("Regional Blood Center - Kumasi")
                    .address("45 Hospital Avenue")
                    .location("Kumasi")
                    .latitude(6.6885)
                    .longitude(-1.6244)
                    .phone("+233244567890")
                    .email("kumasi@bloodbank.com")
                    .operatingHours("Mon-Fri 7:00-19:00")
                    .availableServices(List.of("Donation", "Storage", "Emergency Supply"))
                    .build());
            bloodBankRepository.save(BloodBank.builder()
                    .displayCode("BB003")
                    .name("National Blood Bank Network - Tamale")
                    .address("12 Civic Center Road")
                    .location("Tamale")
                    .latitude(9.4034)
                    .longitude(-0.8424)
                    .phone("+233207654321")
                    .email("tamale@bloodbank.com")
                    .operatingHours("24/7 Emergency")
                    .availableServices(List.of("Emergency", "Inter-Bank Transfer", "Storage"))
                    .build());
        }
    }

    private void initializeDemoInventory() {
        if (bloodUnitRepository.count() >= 20) {
            return;
        }
        BloodGroup[] groups = {
                BloodGroup.O_POSITIVE,
                BloodGroup.A_POSITIVE,
                BloodGroup.B_POSITIVE,
                BloodGroup.AB_POSITIVE,
        };
        java.time.LocalDate today = java.time.LocalDate.now();
        int seeded = 0;
        for (BloodGroup group : groups) {
            for (int i = 0; i < 5; i++) {
                bloodUnitRepository.save(BloodUnit.builder()
                        .id(displayCodeService.nextBloodUnitCode(BloodProductType.WB))
                        .bloodGroup(group)
                        .bloodProductType(BloodProductType.WB)
                        .collectionDate(today.minusDays(3))
                        .expiryDate(BloodBankUtils.calculateExpiryDate(today.minusDays(3), BloodProductType.WB))
                        .status(UnitStatus.Available)
                        .location("Cold Storage A")
                        .build());
                seeded++;
            }
        }
        log.info("Seeded {} demo blood unit(s) for hospital request fulfillment", seeded);
    }

    private void initializeDemoSupplyRequests() {
        if (supplyRequestRepository.count() > 0) {
            return;
        }
        var suppliers = bloodBankRepository.findAll();
        if (suppliers.isEmpty()) {
            return;
        }
        BloodBank supplier = suppliers.size() > 1 ? suppliers.get(1) : suppliers.get(0);
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Map<String, Object>> followUp = new ArrayList<>();
        followUp.add(Map.of(
                "timestamp", java.time.LocalDateTime.now().minusDays(2).toString(),
                "authorName", "Staff Member",
                "note", "Request submitted to " + supplier.getName(),
                "status", "Submitted"));
        followUp.add(Map.of(
                "timestamp", java.time.LocalDateTime.now().minusDays(1).toString(),
                "authorName", supplier.getName(),
                "note", "Request acknowledged. Preparing 15 units for dispatch.",
                "status", "Acknowledged"));
        supplyRequestRepository.save(SupplyRequest.builder()
                .displayCode(displayCodeService.nextCode(EntityType.SUPPLY_REQUEST))
                .bloodGroup(BloodGroup.O_POSITIVE)
                .bloodProductType(BloodProductType.WB)
                .unitsRequested(15)
                .urgency(Urgency.Critical)
                .status(SupplyRequestStatus.Acknowledged)
                .supplierBloodBankId(supplier.getId())
                .supplierBloodBankName(supplier.getName())
                .requiredBy(today.plusDays(1))
                .reason("Critical shortage of O+ whole blood due to increased hospital demand.")
                .currentUnits(8)
                .capacity(100)
                .requestedByName("Staff Member")
                .followUpNotes(followUp)
                .build());
        log.info("Seeded demo inter-bank supply request");
    }

    private void initializeDemoUsers() {
        String encodedPassword = passwordEncoder.encode("demo123");
        createUserIfNotExists("admin@bloodbank.com", "Admin User", "admin", encodedPassword, null);
        User staffUser = createUserIfNotExists("staff@bloodbank.com", "Staff Member", "staff", encodedPassword, "Senior Staff");
        createUserIfNotExists("donor@email.com", "John Donor", "donor", encodedPassword, null);
        createUserIfNotExists("hospital@stmarys.com", "St. Mary's Hospital", "hospital", encodedPassword, null);
        createUserIfNotExists("specialist@bloodbank.com", "Specialist User", "specialist", encodedPassword, "Senior Staff");

        if (staffUser != null && staffRepository.findByUserId(staffUser.getId()).isEmpty()) {
            StaffRole senior = staffRoleRepository.findByName("Senior Staff").orElseThrow();
            staffRepository.save(Staff.builder()
                    .user(staffUser)
                    .name(staffUser.getName())
                    .email(staffUser.getEmail())
                    .staffRole(senior)
                    .department(com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffDepartment.Collection)
                    .status(com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffStatus.Active)
                    .build());
        }

        User donorUser = userRepository.findByEmail("donor@email.com").orElse(null);
        if (donorUser != null && donorRepository.findByUserId(donorUser.getId()).isEmpty()) {
            Donor donor = donorRepository.save(Donor.builder()
                    .displayCode(displayCodeService.nextCode(com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType.DONOR))
                    .user(donorUser)
                    .firstName("John")
                    .lastName("Donor")
                    .dateOfBirth(java.time.LocalDate.of(1995, 5, 15))
                    .gender(com.bloodbank.bloodbank.entity.enums.DomainEnums.Gender.Male)
                    .idType(com.bloodbank.bloodbank.entity.enums.DomainEnums.IdType.Ghana_Card)
                    .idNumber("GHA-123456789-0")
                    .bloodGroup(com.bloodbank.bloodbank.entity.enums.BloodGroup.O_POSITIVE)
                    .status(com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus.Eligible)
                    .build());
            donorRewardRepository.save(DonorReward.builder().donorId(donor.getId()).build());
        }

        User hospitalUser = userRepository.findByEmail("hospital@stmarys.com").orElse(null);
        if (hospitalUser != null && hospitalRepository.findByUserId(hospitalUser.getId()).isEmpty()) {
            hospitalRepository.save(Hospital.builder()
                    .displayCode(displayCodeService.nextCode(com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType.HOSPITAL))
                    .user(hospitalUser)
                    .name("St. Mary's Hospital")
                    .registrationNumber("HOSP-STM-001")
                    .location("Accra")
                    .address("456 Hospital Ave")
                    .latitude(5.6100)
                    .longitude(-0.1900)
                    .phone("+233209876543")
                    .email("hospital@stmarys.com")
                    .capacity(com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalCapacity.Large)
                    .beds(200)
                    .departments(List.of("Emergency", "Surgery", "ICU"))
                    .totalRequests(0)
                    .pendingRequests(0)
                    .build());
        }

        hospitalRepository.findAll().forEach(h -> {
            boolean changed = false;
            if (h.getTotalRequests() == null) {
                h.setTotalRequests(0);
                changed = true;
            }
            if (h.getPendingRequests() == null) {
                h.setPendingRequests(0);
                changed = true;
            }
            if (changed) {
                hospitalRepository.save(h);
            }
        });
    }

    private User createUserIfNotExists(String email, String name, String roleName, String password, String staffRoleName) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (userRepository.existsByEmailIncludingDeleted(email)) {
            log.warn("User {} already exists in database (possibly soft-deleted), skipping seed create", email);
            return null;
        }
        try {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            User user = userRepository.save(User.builder()
                    .email(email).name(name).password(password).role(role).active(true).emailVerified(true).build());
            log.info("Created user: {} ({})", email, roleName);

            if (staffRoleName != null && ("staff".equals(roleName) || "specialist".equals(roleName))) {
                StaffRole sr = staffRoleRepository.findByName(staffRoleName).orElseThrow();
                staffRepository.save(Staff.builder()
                        .user(user).name(name).email(email).staffRole(sr)
                        .department(com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffDepartment.Testing)
                        .status(com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffStatus.Active)
                        .build());
            }
            return user;
        } catch (DataIntegrityViolationException ex) {
            log.warn("User {} already exists (skipping seed create): {}", email, ex.getMostSpecificCause().getMessage());
            return userRepository.findByEmail(email).orElse(null);
        }
    }
}
