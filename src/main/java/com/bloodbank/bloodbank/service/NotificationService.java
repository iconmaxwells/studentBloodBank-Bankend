package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Notification;
import com.bloodbank.bloodbank.entity.User;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.NotificationType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.notification.EmailNotificationService;
import com.bloodbank.bloodbank.notification.SmsNotificationService;
import com.bloodbank.bloodbank.repository.NotificationRepository;
import com.bloodbank.bloodbank.repository.UserRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailNotificationService emailNotificationService;
    private final SmsNotificationService smsNotificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> listNotifications(int page, int limit) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ApiException("UNAUTHORIZED", "Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        PageRequest pageable = PageUtils.toPageRequest(page, limit, "-createdAt");
        Page<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    public Notification markRead(UUID id) {
        Notification notification = getOwnedNotification(id);
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    public void markAllRead() {
        UUID userId = SecurityUtils.getCurrentUserId();
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1000))
                .forEach(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public Notification notifyUser(UUID userId, NotificationType type, String title, String message,
                                   String relatedEntityType, String relatedEntityId) {
        if (!userRepository.existsById(userId)) {
            return null;
        }
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build();
        Notification saved = notificationRepository.save(notification);
        deliverExternalChannels(userId, type, title, message);
        return saved;
    }

    public void notifyDonor(UUID userId, String title, String message, String entityType, String entityId) {
        notifyUser(userId, NotificationType.info, title, message, entityType, entityId);
    }

    public void notifyStaff(String title, String message, String entityType, String entityId) {
        userRepository.findAll().stream()
                .filter(u -> "staff".equalsIgnoreCase(u.getRole().getName())
                        || "admin".equalsIgnoreCase(u.getRole().getName()))
                .forEach(u -> notifyUser(u.getId(), NotificationType.urgent, title, message, entityType, entityId));
    }

    private void deliverExternalChannels(UUID userId, NotificationType type, String title, String message) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getEmail() != null) {
                emailNotificationService.send(user.getEmail(), title, message);
            }
            if (user.getPhone() != null && (type == NotificationType.critical || type == NotificationType.urgent)) {
                smsNotificationService.send(user.getPhone(), title + ": " + message);
            }
        });
    }

    private Notification getOwnedNotification(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification"));
        if (!notification.getUserId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
        return notification;
    }
}
