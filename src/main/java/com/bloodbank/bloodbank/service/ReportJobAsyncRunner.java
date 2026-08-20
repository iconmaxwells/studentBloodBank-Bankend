package com.bloodbank.bloodbank.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ReportJobAsyncRunner {

    private final ReportService reportService;

    public ReportJobAsyncRunner(@Lazy ReportService reportService) {
        this.reportService = reportService;
    }

    @Async
    public void runJob(UUID jobId, Map<String, Object> request) {
        reportService.executeJob(jobId, request);
    }
}
