package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.CompensationMethod;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simulates external payment gateways (MTN MoMo, bank transfer, cash desk)
 * for demo / academic use — no real money movement.
 */
@Service
public class PaymentSimulatorService {

    public Map<String, Object> simulate(CompensationMethod method, double amount, String phoneNumber) {
        if (amount <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Payment amount must be positive");
        }

        String reference = "SIM-" + method.name().substring(0, Math.min(3, method.name().length()))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String provider = switch (method) {
            case Mobile_Money -> "MTN Mobile Money (Simulator)";
            case Bank_Transfer -> "GhIPSS Instant Pay (Simulator)";
            case Cash -> "Cash Desk (Simulator)";
        };

        String maskedPhone = phoneNumber != null && phoneNumber.length() >= 4
                ? "***" + phoneNumber.replaceAll("\\D", "").substring(Math.max(0, phoneNumber.replaceAll("\\D", "").length() - 4))
                : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("reference", reference);
        result.put("provider", provider);
        result.put("method", method.name());
        result.put("amount", amount);
        result.put("currency", "GHS");
        result.put("maskedPhone", maskedPhone);
        result.put("message", "Simulated payment of GHS " + amount + " approved via " + provider);
        return result;
    }

    public static CompensationMethod parseMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            return CompensationMethod.Cash;
        }
        String normalized = raw.trim()
                .replace(' ', '_')
                .replace('-', '_');
        if ("Mobile_Money".equalsIgnoreCase(normalized) || "MOMO".equalsIgnoreCase(normalized)) {
            return CompensationMethod.Mobile_Money;
        }
        if ("Bank_Transfer".equalsIgnoreCase(normalized) || "BANK".equalsIgnoreCase(normalized)) {
            return CompensationMethod.Bank_Transfer;
        }
        if ("Cash".equalsIgnoreCase(normalized)) {
            return CompensationMethod.Cash;
        }
        try {
            return CompensationMethod.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return CompensationMethod.Cash;
        }
    }
}
