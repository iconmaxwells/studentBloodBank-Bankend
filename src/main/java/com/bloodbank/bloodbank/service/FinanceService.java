package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Transaction;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TransactionStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TransactionType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.repository.TransactionRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FinanceService {

    private final TransactionRepository transactionRepository;
    private final DisplayCodeService displayCodeService;
    private final ActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        requireAdmin();
        List<Transaction> all = transactionRepository.findAll();
        double revenue = all.stream()
                .filter(t -> t.getType() == TransactionType.Revenue && t.getStatus() == TransactionStatus.Completed)
                .mapToDouble(Transaction::getAmount)
                .sum();
        double expenses = all.stream()
                .filter(t -> t.getType() == TransactionType.Expense && t.getStatus() == TransactionStatus.Completed)
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .sum();
        return Map.of(
                "revenue", revenue,
                "expenses", expenses,
                "profit", revenue - expenses,
                "pendingTransactions", all.stream().filter(t -> t.getStatus() == TransactionStatus.Pending).count()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listTransactions(TransactionType type, int page, int limit, String sort) {
        requireAdmin();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<Transaction> result = type != null
                ? transactionRepository.findByType(type, pageable)
                : transactionRepository.findAll(pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    public Transaction createTransaction(Transaction transaction) {
        requireAdmin();
        transaction.setDisplayCode(displayCodeService.nextCode(EntityType.TRANSACTION));
        if (transaction.getStatus() == null) {
            transaction.setStatus(TransactionStatus.Completed);
        }
        if (transaction.getType() == TransactionType.Expense && transaction.getAmount() > 0) {
            transaction.setAmount(-transaction.getAmount());
        }
        Transaction saved = transactionRepository.save(transaction);
        activityLogService.log(ActionType.create, "create_transaction",
                "Created transaction " + saved.getDisplayCode(), "finance", null, null, null, null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyChartData(int months) {
        requireAdmin();
        List<Transaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.Completed)
                .collect(Collectors.toList());

        List<String> labels = new ArrayList<>();
        List<Double> revenueData = new ArrayList<>();
        List<Double> expenseData = new ArrayList<>();

        YearMonth current = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            labels.add(month.toString());
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();

            double revenue = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.Revenue
                            && !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
                    .mapToDouble(Transaction::getAmount)
                    .sum();
            double expenses = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.Expense
                            && !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
                    .mapToDouble(t -> Math.abs(t.getAmount()))
                    .sum();
            revenueData.add(revenue);
            expenseData.add(expenses);
        }

        return Map.of("labels", labels, "revenue", revenueData, "expenses", expenseData);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBreakdownChart() {
        requireAdmin();
        List<Transaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.Completed)
                .collect(Collectors.toList());

        Map<String, Double> revenueByCategory = transactions.stream()
                .filter(t -> t.getType() == TransactionType.Revenue)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() != null ? t.getCategory() : "Other",
                        Collectors.summingDouble(Transaction::getAmount)));

        Map<String, Double> expenseByCategory = transactions.stream()
                .filter(t -> t.getType() == TransactionType.Expense)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() != null ? t.getCategory() : "Other",
                        Collectors.summingDouble(t -> Math.abs(t.getAmount()))));

        return Map.of("revenueBreakdown", revenueByCategory, "expenseBreakdown", expenseByCategory);
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }
}
