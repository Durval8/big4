package com.financedash.controller;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.dto.PageResponse;
import com.financedash.dto.TransactionRequest;
import com.financedash.dto.TransactionResponse;
import com.financedash.dto.TransactionSortBy;
import com.financedash.exception.InvalidTransactionException;
import com.financedash.service.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public PageResponse<TransactionResponse> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "DATE") TransactionSortBy sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidTransactionException("size must not exceed " + MAX_PAGE_SIZE);
        }
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Sort sort = Sort.by(sortDir, sortBy.field()).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Transaction> result =
                transactionService.findAll(effectiveFrom, effectiveTo, accountType, category, pageable);
        return PageResponse.from(result, TransactionResponse::from);
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
