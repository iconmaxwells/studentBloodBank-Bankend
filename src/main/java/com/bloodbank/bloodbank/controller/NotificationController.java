package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.Notification;
import com.bloodbank.bloodbank.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean read) {
        return ControllerUtils.paged(notificationService.listNotifications(page, limit));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount() {
        return ApiResponse.ok(Map.of("count", notificationService.getUnreadCount()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Notification> markRead(@PathVariable UUID id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, String>> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.ok(Map.of("message", "All notifications marked as read"));
    }
}
