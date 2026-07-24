package com.financedash.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.financedash.repository.InvestmentEventRepository;
import com.financedash.repository.InvestmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;
    @Mock
    private InvestmentEventRepository eventRepository;

    @InjectMocks
    private InvestmentService service;

    private static Investment holding(long id, String symbol, String value, InvestmentStatus status) {
        Investment i = new Investment(symbol, new BigDecimal(value));
        i.setStatus(status);
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    private static InvestmentEvent fund(String amount) {
        return new InvestmentEvent(new Investment("X", BigDecimal.ZERO),
                InvestmentEventType.FUND, new BigDecimal(amount), AccountType.CHECKING, LocalDate.of(2026, 6, 1));
    }

    private static InvestmentEvent cashOut(String amount) {
        return new InvestmentEvent(new Investment("X", BigDecimal.ZERO),
                InvestmentEventType.CASH_OUT, new BigDecimal(amount), AccountType.SAVINGS, LocalDate.of(2026, 6, 1));
    }

    private void stubSaveWithId(long id) {
        when(investmentRepository.save(any())).thenAnswer(inv -> {
            Investment a = inv.getArgument(0);
            if (ReflectionTestUtils.getField(a, "id") == null) {
                ReflectionTestUtils.setField(a, "id", id);
            }
            return a;
        });
    }

    @Test
    void createRejectsInvestingSource() {
        InvestmentRequest req = new InvestmentRequest("AAPL", new BigDecimal("100.00"), AccountType.INVESTING);
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(InvalidInvestmentException.class)
                .hasMessageContaining("CHECKING or SAVINGS");
        verify(investmentRepository, never()).save(any());
    }

    @Test
    void createNewHoldingNormalizesSymbol() {
        when(investmentRepository.findFirstByStockSymbolAndStatus("AAPL", InvestmentStatus.OPEN))
                .thenReturn(Optional.empty());
        stubSaveWithId(1L);
        when(eventRepository.findByInvestmentId(1L)).thenReturn(List.of(fund("100.00")));

        InvestmentResponse r = service.create(new InvestmentRequest(" aapl ", new BigDecimal("100.00"), AccountType.CHECKING));

        assertThat(r.stockSymbol()).isEqualTo("AAPL");
        assertThat(r.currentValue()).isEqualByComparingTo("100.00");
        assertThat(r.netCashInvested()).isEqualByComparingTo("100.00");
        assertThat(r.positionChangePct()).isEqualByComparingTo("0.00");
        assertThat(r.status()).isEqualTo(InvestmentStatus.OPEN);
    }

    @Test
    void createMergesExistingOpenSymbol() {
        Investment existing = holding(1L, "AAPL", "100.00", InvestmentStatus.OPEN);
        when(investmentRepository.findFirstByStockSymbolAndStatus("AAPL", InvestmentStatus.OPEN))
                .thenReturn(Optional.of(existing));
        stubSaveWithId(1L);
        when(eventRepository.findByInvestmentId(1L)).thenReturn(List.of(fund("100.00"), fund("50.00")));

        InvestmentResponse r = service.create(new InvestmentRequest("AAPL", new BigDecimal("50.00"), AccountType.CHECKING));

        assertThat(r.currentValue()).isEqualByComparingTo("150.00");
        assertThat(r.netCashInvested()).isEqualByComparingTo("150.00");
    }

    @Test
    void editMarksToMarket_positionChangePlus50() {
        Investment existing = holding(1L, "AAPL", "100.00", InvestmentStatus.OPEN);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubSaveWithId(1L);
        when(eventRepository.findByInvestmentId(1L)).thenReturn(List.of(fund("100.00")));

        InvestmentResponse r = service.update(1L, new InvestmentUpdateRequest("AAPL", new BigDecimal("150.00")));

        assertThat(r.currentValue()).isEqualByComparingTo("150.00");
        assertThat(r.netCashInvested()).isEqualByComparingTo("100.00");
        assertThat(r.positionChangePct()).isEqualByComparingTo("50.00"); // €100 → €150 = +50%
    }

    @Test
    void editMarkDown_positionChangeNegative() {
        Investment existing = holding(1L, "AAPL", "100.00", InvestmentStatus.OPEN);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubSaveWithId(1L);
        when(eventRepository.findByInvestmentId(1L)).thenReturn(List.of(fund("100.00")));

        InvestmentResponse r = service.update(1L, new InvestmentUpdateRequest("AAPL", new BigDecimal("80.00")));

        assertThat(r.positionChangePct()).isEqualByComparingTo("-20.00");
    }

    @Test
    void editCashedOutThrows() {
        when(investmentRepository.findById(1L))
                .thenReturn(Optional.of(holding(1L, "AAPL", "0.00", InvestmentStatus.CASHED_OUT)));
        assertThatThrownBy(() -> service.update(1L, new InvestmentUpdateRequest("AAPL", new BigDecimal("10.00"))))
                .isInstanceOf(InvalidInvestmentException.class);
        verify(investmentRepository, never()).save(any());
    }

    @Test
    void partialCashOutKeepsOpen() {
        Investment existing = holding(1L, "AAPL", "100.00", InvestmentStatus.OPEN);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubSaveWithId(1L);
        when(eventRepository.findByInvestmentId(1L)).thenReturn(List.of(fund("100.00"), cashOut("40.00")));

        InvestmentResponse r = service.cashOut(1L, new CashOutRequest(new BigDecimal("40.00")));

        assertThat(r.currentValue()).isEqualByComparingTo("60.00");
        assertThat(r.status()).isEqualTo(InvestmentStatus.OPEN);
        verify(eventRepository).save(any());
    }

    @Test
    void fullCashOutClosesAndPctNull() {
        Investment existing = holding(1L, "AAPL", "100.00", InvestmentStatus.OPEN);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubSaveWithId(1L);
        when(eventRepository.findByInvestmentId(1L)).thenReturn(List.of(fund("100.00"), cashOut("100.00")));

        InvestmentResponse r = service.cashOut(1L, new CashOutRequest(new BigDecimal("100.00")));

        assertThat(r.currentValue()).isEqualByComparingTo("0.00");
        assertThat(r.status()).isEqualTo(InvestmentStatus.CASHED_OUT);
        assertThat(r.netCashInvested()).isEqualByComparingTo("0"); // net ≤ 0
        assertThat(r.positionChangePct()).isNull();
    }

    @Test
    void cashOutExceedingPositionThrows() {
        when(investmentRepository.findById(1L))
                .thenReturn(Optional.of(holding(1L, "AAPL", "100.00", InvestmentStatus.OPEN)));
        assertThatThrownBy(() -> service.cashOut(1L, new CashOutRequest(new BigDecimal("200.00"))))
                .isInstanceOf(InvalidInvestmentException.class)
                .hasMessageContaining("exceeds");
        verify(eventRepository, never()).save(any());
    }

    @Test
    void summaryAggregatesOpenHoldings() {
        Investment a = holding(1L, "AAPL", "150.00", InvestmentStatus.OPEN);
        Investment b = holding(2L, "MSFT", "300.00", InvestmentStatus.OPEN);
        when(investmentRepository.findByStatus(InvestmentStatus.OPEN)).thenReturn(List.of(a, b));
        when(eventRepository.findAll()).thenReturn(List.of(
                new InvestmentEvent(a, InvestmentEventType.FUND, new BigDecimal("100.00"), AccountType.CHECKING, LocalDate.of(2026, 6, 1)),
                new InvestmentEvent(b, InvestmentEventType.FUND, new BigDecimal("300.00"), AccountType.CHECKING, LocalDate.of(2026, 6, 1))));

        InvestmentSummaryResponse s = service.summary();

        assertThat(s.totalCurrentValue()).isEqualByComparingTo("450.00");
        assertThat(s.totalNetInvested()).isEqualByComparingTo("400.00");
        assertThat(s.positionChangePct()).isEqualByComparingTo("12.50");
    }
}
