package com.financedash.controller;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.dto.TransactionRequest;
import com.financedash.dto.TransactionResponse;
import com.financedash.service.TransactionService;
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
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionResponse> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(required = false) Category category) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        return transactionService.findAll(effectiveFrom, effectiveTo, accountType, category).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public TransactionResponse findById(@PathVariable Long id) {
        return TransactionResponse.from(transactionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        Transaction created = transactionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(created));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
