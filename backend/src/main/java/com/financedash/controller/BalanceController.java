package com.financedash.controller;

import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.dto.Period;
import com.financedash.dto.TimeRange;
import com.financedash.service.BalanceService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    /**
     * Either pass {@code range} (WEEK/MONTH/YEAR/ALL, resolved against today) or an
     * explicit {@code from}/{@code to} pair. {@code range} wins if both are supplied.
     */
    @GetMapping
    public BalanceSummaryResponse summarize(
            @RequestParam(required = false) TimeRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Period period = Period.resolve(range, from, to, LocalDate.now());
        return balanceService.summarize(period.from(), period.to());
    }
}
