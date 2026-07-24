package com.financedash.controller;

import com.financedash.dto.CashOutRequest;
import com.financedash.dto.InvestmentRequest;
import com.financedash.dto.InvestmentResponse;
import com.financedash.dto.InvestmentSummaryResponse;
import com.financedash.dto.InvestmentUpdateRequest;
import com.financedash.service.InvestmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService investmentService;

    public InvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @GetMapping
    public List<InvestmentResponse> findAll() {
        return investmentService.findAll();
    }

    @GetMapping("/summary")
    public InvestmentSummaryResponse summary() {
        return investmentService.summary();
    }

    @GetMapping("/{id}")
    public InvestmentResponse findById(@PathVariable Long id) {
        return investmentService.findById(id);
    }

    @PostMapping
    public ResponseEntity<InvestmentResponse> create(@Valid @RequestBody InvestmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(investmentService.create(request));
    }

    @PutMapping("/{id}")
    public InvestmentResponse update(@PathVariable Long id, @Valid @RequestBody InvestmentUpdateRequest request) {
        return investmentService.update(id, request);
    }

    @PostMapping("/{id}/cash-out")
    public InvestmentResponse cashOut(@PathVariable Long id, @Valid @RequestBody CashOutRequest request) {
        return investmentService.cashOut(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        investmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
