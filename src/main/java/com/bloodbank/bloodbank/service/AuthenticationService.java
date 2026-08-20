package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.dto.*;
import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.security.JwtTokenProvider;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StaffRepository staffRepository;
    private final DonorRepository donorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final DisplayCodeService displayCodeService;
    private final SystemSettingsService systemSettingsService;
    private final ActivityLogService activityLogService;
    private final DonorRewardRepository donorRewardRepository;

    @Value("${app.jwt.expiration:3600000}")
    private long jwtExpirationMs;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndRole_Name(request.getEmail(), request.getRole())
                .orElseThrow(() -> new ApiException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
        if (!user.getActive()) {
            throw new ApiException("ACCOUNT_INACTIVE", "User account is inactive", HttpStatus.FORBIDDEN);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        activityLogService.log(ActionType.auth, "login", "User logged in", "auth");
        return buildLoginResponse(user);
    }

    public LoginResponse registerDonor(DonorRegisterRequest request) {
        if (!Boolean.TRUE.equals(request.getAgreeToTerms())) {
            throw new BusinessRuleException("TERMS_REQUIRED", "You must agree to terms");
        }
        SystemSettings settings = systemSettingsService.getSettings();
        int age = Period.between(request.getDateOfBirth(), LocalDate.now()).getYears();
        if (age < settings.getMinAge() || age > settings.getMaxAge()) {
            throw new BusinessRuleException("AGE_INVALID", "Donor age must be between " + settings.getMinAge() + " and " + settings.getMaxAge());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("EMAIL_EXISTS", "Email already registered", HttpStatus.CONFLICT);
        }
        if (donorRepository.existsByIdNumber(request.getIdNumber())) {
            throw new ApiException("ID_EXISTS", "ID number already registered", HttpStatus.CONFLICT);
        }
        Role role = roleRepository.findByName("donor")
                .orElseThrow(() -> new ApiException("ROLE_NOT_FOUND", "Donor role not found", HttpStatus.INTERNAL_SERVER_ERROR));

        User user = userRepository.save(User.builder()
                .name(request.getFirstName() + " " + request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(true)
                .emailVerified(false)
                .build());

        Donor donor = donorRepository.save(Donor.builder()
                .displayCode(displayCodeService.nextCode(EntityType.DONOR))
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .idType(request.getIdType())
                .idNumber(request.getIdNumber())
                .status(DonorStatus.Pending_Screening)
                .isVoluntary(Boolean.TRUE.equals(request.getIsVoluntary()))
                .build());

        donorRewardRepository.save(DonorReward.builder().donorId(donor.getId()).build());
        activityLogService.log(ActionType.create, "register_donor", "Donor registered: " + donor.getDisplayCode(), "donor", null, donor.getId(), null, null, null);
        return buildLoginResponse(user);
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new ApiException("INVALID_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        if (!"refresh".equals(jwtTokenProvider.getTokenType(request.getRefreshToken()))) {
            throw new ApiException("INVALID_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        String hash = hashToken(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new ApiException("INVALID_TOKEN", "Refresh token revoked or not found", HttpStatus.UNAUTHORIZED));
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException("TOKEN_EXPIRED", "Refresh token expired", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "User not found", HttpStatus.UNAUTHORIZED));
        return buildLoginResponse(user);
    }

    public void logout() {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            refreshTokenRepository.deleteByUserId(userId);
            activityLogService.log(ActionType.auth, "logout", "User logged out", "auth");
        }
    }

    public void changePassword(ChangePasswordRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ApiException("INVALID_PASSWORD", "Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(userId);
        activityLogService.log(ActionType.auth, "change_password", "Password changed", "auth");
    }

    public Map<String, Object> verifyAction(VerifyActionRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException("INVALID_PASSWORD", "Password verification failed", HttpStatus.UNAUTHORIZED);
        }
        String actionToken = jwtTokenProvider.generateActionToken(userId, "sensitive_action");
        return Map.of("actionToken", actionToken, "expiresIn", 300);
    }

    public Map<String, Object> verifyAdminPassword(VerifyActionRequest request) {
        UserPrincipalCheck();
        return verifyAction(request);
    }

    public UserResponse me() {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ApiException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        return toUserResponse(user);
    }

    private void UserPrincipalCheck() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }

    private LoginResponse buildLoginResponse(User user) {
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().getName());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());
        storeRefreshToken(user.getId(), refreshToken);
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpirationMs / 1000)
                .user(toUserResponse(user))
                .build();
    }

    private void storeRefreshToken(UUID userId, String refreshToken) {
        refreshTokenRepository.deleteByUserId(userId);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build());
    }

    private UserResponse toUserResponse(User user) {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        String staffRoleName = null;
        if ("staff".equalsIgnoreCase(user.getRole().getName()) || "specialist".equalsIgnoreCase(user.getRole().getName())) {
            Staff staff = staffRepository.findByUserId(user.getId()).orElse(null);
            if (staff != null && staff.getStaffRole() != null) {
                staffRoleName = staff.getStaffRole().getName();
                staff.getStaffRole().getPermissions().forEach(p -> permissions.put(p.getName(), true));
            }
            permissions.put("canApproveRequests", true);
            permissions.put("canRejectRequests", true);
        }
        if ("admin".equalsIgnoreCase(user.getRole().getName())) {
            permissions.put("canApproveRequests", true);
            permissions.put("canRejectRequests", true);
            permissions.put("canManageInventory", true);
            permissions.put("canManageDonors", true);
            permissions.put("canConductWithdrawals", true);
            permissions.put("canViewReports", true);
            permissions.put("canManageStaff", true);
        }
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .staffRole(staffRoleName)
                .permissions(permissions)
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
