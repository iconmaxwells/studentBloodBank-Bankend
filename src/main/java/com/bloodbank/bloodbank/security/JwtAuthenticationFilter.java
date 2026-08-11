package com.bloodbank.bloodbank.security;

import com.bloodbank.bloodbank.entity.Staff;
import com.bloodbank.bloodbank.entity.User;
import com.bloodbank.bloodbank.repository.StaffRepository;
import com.bloodbank.bloodbank.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractTokenFromRequest(request);
            if (token != null && jwtTokenProvider.validateToken(token)
                    && !"refresh".equals(jwtTokenProvider.getTokenType(token))
                    && !"action".equals(jwtTokenProvider.getTokenType(token))) {
                UUID userId = jwtTokenProvider.getUserIdFromToken(token);
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.getActive()) {
                    UserPrincipal principal = buildPrincipal(user);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("JWT authentication filter error: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private UserPrincipal buildPrincipal(User user) {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        String staffRoleName = null;
        String roleName = user.getRole().getName();
        if ("admin".equalsIgnoreCase(roleName)) {
            grantAllStaffPermissions(permissions);
        } else if ("staff".equalsIgnoreCase(roleName) || "specialist".equalsIgnoreCase(roleName)) {
            Staff staff = staffRepository.findByUserId(user.getId()).orElse(null);
            if (staff != null && staff.getStaffRole() != null) {
                staffRoleName = staff.getStaffRole().getName();
                staff.getStaffRole().getPermissions().forEach(p -> permissions.put(p.getName(), true));
            }
            // All staff portal users may approve/reject hospital requests
            permissions.put("canApproveRequests", true);
            permissions.put("canRejectRequests", true);
        }
        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .staffRole(staffRoleName)
                .permissions(permissions)
                .build();
    }

    private void grantAllStaffPermissions(Map<String, Boolean> permissions) {
        for (String name : List.of(
                "canApproveRequests", "canRejectRequests", "canManageInventory",
                "canManageDonors", "canConductWithdrawals", "canViewReports", "canManageStaff")) {
            permissions.put(name, true);
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.equals("/api/v1/health") || path.equals("/health")) {
            return true;
        }
        if (!path.startsWith("/api/v1/auth/")) {
            return false;
        }
        // Only skip JWT parsing for public auth endpoints.
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register/donor")
                || path.equals("/api/v1/auth/refresh");
    }
}
