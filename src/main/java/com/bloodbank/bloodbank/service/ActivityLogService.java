package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.ActivityLog;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.repository.ActivityLogRepository;
import com.bloodbank.bloodbank.security.UserPrincipal;
import com.bloodbank.bloodbank.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public void log(ActionType actionType, String action, String description, String category) {
        log(actionType, action, description, category, null, null, null, null, null);
    }

    public void log(ActionType actionType, String action, String description, String category,
                    UUID requestId, UUID donorId, UUID hospitalId, UUID collectionId, Map<String, Object> metadata) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        ActivityLog log = ActivityLog.builder()
                .action(action)
                .actionType(actionType)
                .description(description)
                .category(category)
                .staffId(principal != null ? principal.getId() : null)
                .staffName(principal != null ? principal.getEmail() : "system")
                .staffRole(principal != null ? (principal.getStaffRole() != null ? principal.getStaffRole() : principal.getRole()) : null)
                .requestId(requestId)
                .donorId(donorId)
                .hospitalId(hospitalId)
                .collectionId(collectionId)
                .metadata(metadata)
                .ipAddress(resolveIp())
                .build();
        activityLogRepository.save(log);
    }

    private String resolveIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
