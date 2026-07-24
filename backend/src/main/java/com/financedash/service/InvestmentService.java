package com.financedash.service;

import com.financedash.domain.AccountType;
import com.financedash.domain.Investment;
import com.financedash.domain.InvestmentEvent;
import com.financedash.domain.InvestmentEventType;
import com.financedash.domain.InvestmentStatus;
import com.financedash.dto.CashOutRequest;
import com.financedash.dto.InvestmentRequest;
import com.financedash.dto.InvestmentResponse;
import com.financedash.dto.InvestmentSummaryResponse;
import com.financedash.dto.InvestmentUpdateRequest;
import com.financedash.exception.InvalidInvestmentException;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.repository.InvestmentEventRepository;
import com.financedash.repository.InvestmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final InvestmentEventRepository eventRepository;

    public InvestmentService(InvestmentRepository investmentRepository,
                             InvestmentEventRepository eventRepository) {
        this.investmentRepository = investmentRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<InvestmentResponse> findAll() {
        Map<Long, BigDecimal> netByInvestment = netCashInvestedByInvestment();
        return investmentRepository.findAllByOrderByStockSymbolAsc().stream()
                .map(inv -> toResponse(inv, netByInvestment.getOrDefault(inv.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public InvestmentResponse findById(Long id) {
        Investment inv = require(id);
        return toResponse(inv, netCashInvested(inv.getId()));
    }

    public InvestmentResponse create(InvestmentRequest request) {
        AccountType source = request.sourceAccount();
        if (source != AccountType.CHECKING && source != AccountType.SAVINGS) {
            throw new InvalidInvestmentException("sourceAccount must be CHECKING or SAVINGS");
        }
        String symbol = normalize(request.stockSymbol());

        // Merge into an existing OPEN holding of the same symbol, else create a new one.
        Investment inv = investmentRepository
                .findFirstByStockSymbolAndStatus(symbol, InvestmentStatus.OPEN)
                .orElse(null);
        if (inv == null) {
            inv = investmentRepository.save(new Investment(symbol, request.amount()));
        } else {
            inv.setCurrentValue(inv.getCurrentValue().add(request.amount()));
            inv = investmentRepository.save(inv);
        }

        eventRepository.save(new InvestmentEvent(
                inv, InvestmentEventType.FUND, request.amount(), source, LocalDate.now()));

        return toResponse(inv, netCashInvested(inv.getId()));
    }

    public InvestmentResponse update(Long id, InvestmentUpdateRequest request) {
        Investment inv = require(id);
        if (inv.getStatus() != InvestmentStatus.OPEN) {
            throw new InvalidInvestmentException("Cannot edit a cashed-out investment");
        }
        inv.setStockSymbol(normalize(request.stockSymbol()));
        inv.setCurrentValue(request.currentValue()); // mark-to-market, no cash movement
        investmentRepository.save(inv);
        return toResponse(inv, netCashInvested(inv.getId()));
    }

    public InvestmentResponse cashOut(Long id, CashOutRequest request) {
        Investment inv = require(id);
        if (inv.getStatus() != InvestmentStatus.OPEN) {
            throw new InvalidInvestmentException("Investment is already cashed out");
        }
        if (request.amount().compareTo(inv.getCurrentValue()) > 0) {
            throw new InvalidInvestmentException("Cash-out amount exceeds the current position");
        }

        eventRepository.save(new InvestmentEvent(
                inv, InvestmentEventType.CASH_OUT, request.amount(), AccountType.SAVINGS, LocalDate.now()));

        inv.setCurrentValue(inv.getCurrentValue().subtract(request.amount()));
        if (inv.getCurrentValue().compareTo(BigDecimal.ZERO) == 0) {
            inv.setStatus(InvestmentStatus.CASHED_OUT);
        }
        investmentRepository.save(inv);
        return toResponse(inv, netCashInvested(inv.getId()));
    }

    public void delete(Long id) {
        Investment inv = require(id);
        eventRepository.findByInvestmentId(inv.getId()).forEach(eventRepository::delete);
        investmentRepository.delete(inv);
    }

    @Transactional(readOnly = true)
    public InvestmentSummaryResponse summary() {
        Map<Long, BigDecimal> netByInvestment = netCashInvestedByInvestment();
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (Investment inv : investmentRepository.findByStatus(InvestmentStatus.OPEN)) {
            totalCurrent = totalCurrent.add(inv.getCurrentValue());
            totalNet = totalNet.add(netByInvestment.getOrDefault(inv.getId(), BigDecimal.ZERO));
        }
        return new InvestmentSummaryResponse(totalNet, totalCurrent, positionChangePct(totalCurrent, totalNet));
    }

    // --- helpers ---

    private Investment require(Long id) {
        return investmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investment " + id + " not found"));
    }

    private static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal netCashInvested(Long investmentId) {
        BigDecimal net = BigDecimal.ZERO;
        for (InvestmentEvent e : eventRepository.findByInvestmentId(investmentId)) {
            net = e.getType() == InvestmentEventType.FUND
                    ? net.add(e.getAmount())
                    : net.subtract(e.getAmount());
        }
        return net;
    }

    private Map<Long, BigDecimal> netCashInvestedByInvestment() {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (InvestmentEvent e : eventRepository.findAll()) {
            BigDecimal delta = e.getType() == InvestmentEventType.FUND
                    ? e.getAmount()
                    : e.getAmount().negate();
            map.merge(e.getInvestment().getId(), delta, BigDecimal::add);
        }
        return map;
    }

    /** (current − net) / net × 100, scale 2; null when net ≤ 0 (avoids divide-by-zero). */
    private static BigDecimal positionChangePct(BigDecimal currentValue, BigDecimal netCashInvested) {
        if (netCashInvested.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return currentValue.subtract(netCashInvested)
                .divide(netCashInvested, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static InvestmentResponse toResponse(Investment inv, BigDecimal netCashInvested) {
        return new InvestmentResponse(
                inv.getId(),
                inv.getStockSymbol(),
                inv.getCurrentValue(),
                netCashInvested,
                positionChangePct(inv.getCurrentValue(), netCashInvested),
                inv.getStatus(),
                inv.getCreatedAt(),
                inv.getUpdatedAt());
    }
}
