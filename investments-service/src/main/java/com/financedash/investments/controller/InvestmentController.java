package com.financedash.investments.controller;

import com.financedash.investments.dto.BuyRequest;
import com.financedash.investments.dto.CashOutRequest;
import com.financedash.investments.dto.HoldingResponse;
import com.financedash.investments.dto.HoldingUpdateRequest;
import com.financedash.investments.dto.ManualPriceRequest;
import com.financedash.investments.dto.SummaryResponse;
import com.financedash.investments.service.HoldingService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Investments page's API — served entirely by this service (fronted by the gateway). */
@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final HoldingService service;

    public InvestmentController(HoldingService service) {
        this.service = service;
    }

    @GetMapping
    public List<HoldingResponse> list() {
        return service.list();
    }

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public HoldingResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingResponse buy(@Valid @RequestBody BuyRequest request) {
        return service.buy(request);
    }

    @PutMapping("/{id}")
    public HoldingResponse update(@PathVariable String id, @Valid @RequestBody HoldingUpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/cash-out")
    public HoldingResponse cashOut(@PathVariable String id, @Valid @RequestBody CashOutRequest request) {
        return service.cashOut(id, request);
    }

    @PostMapping("/{id}/price")
    public HoldingResponse setManualPrice(@PathVariable String id, @Valid @RequestBody ManualPriceRequest request) {
        return service.setManualPrice(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
