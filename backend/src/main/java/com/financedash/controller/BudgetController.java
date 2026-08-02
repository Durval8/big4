package com.financedash.controller;

import com.financedash.domain.Budget;
import com.financedash.dto.BudgetProgressResponse;
import com.financedash.dto.BudgetRequest;
import com.financedash.dto.BudgetResponse;
import com.financedash.dto.Period;
import com.financedash.dto.TimeRange;
import com.financedash.service.BudgetService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<BudgetResponse> findAll() {
        return budgetService.findAll().stream().map(BudgetResponse::from).toList();
    }

    /**
     * Budgets plus their spend for a period. Accepts the same {@code range} / {@code from}
     * / {@code to} params as {@code GET /api/balances} so the Dashboard shows both under
     * the same window.
     */
    @GetMapping("/progress")
    public List<BudgetProgressResponse> progress(
            @RequestParam(required = false) TimeRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Period period = Period.resolve(range, from, to, LocalDate.now());
        return budgetService.progress(range, period.from(), period.to());
    }

    @GetMapping("/{id}")
    public BudgetResponse findById(@PathVariable Long id) {
        return BudgetResponse.from(budgetService.findById(id));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetRequest request) {
        Budget created = budgetService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BudgetResponse.from(created));
    }

    @PutMapping("/{id}")
    public BudgetResponse update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return BudgetResponse.from(budgetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
