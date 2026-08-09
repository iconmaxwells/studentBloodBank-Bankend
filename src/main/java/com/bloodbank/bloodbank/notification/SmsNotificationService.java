package com.bloodbank.bloodbank.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsNotificationService {

    @Value("${app.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.sms.api-key:}")
    private String apiKey;

    public void send(String phone, String message) {
        if (!smsEnabled || apiKey == null || apiKey.isBlank()) {
            log.info("[SMS-DEV] To: {} | Message: {}", phone, message);
            return;
        }
        log.info("SMS would be sent to {} (provider integration pending)", phone);
    }
}
