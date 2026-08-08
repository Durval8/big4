package com.financedash.controller;

import com.financedash.dto.AnalyticsResponse;
import com.financedash.dto.Period;
import com.financedash.dto.TimeRange;
import com.financedash.service.AnalyticsService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard spending-visualization data. Accepts the same {@code range}/{@code from}/{@code to}
 * params as {@code GET /api/balances} and {@code GET /api/budgets/progress}, but resolves them
 * differently — see {@code AnalyticsService} and
 * docs/superpowers/specs/2026-08-02-transaction-analytics-design.md#window-resolution-and-the-one-year-cap.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public AnalyticsResponse get(
            @RequestParam(required = false) TimeRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Period period = Period.resolve(range, from, to, LocalDate.now());
        return analyticsService.getAnalytics(from, period.from(), period.to());
    }
}
