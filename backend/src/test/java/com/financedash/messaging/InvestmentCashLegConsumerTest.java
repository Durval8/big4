package com.financedash.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedash.domain.AccountType;
import com.financedash.domain.CashLegType;
import com.financedash.domain.InvestmentCashFlow;
import com.financedash.repository.InvestmentCashFlowRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Cash-leg consumer: records new legs, ignores redeliveries (idempotent by eventId). */
@ExtendWith(MockitoExtension.class)
class InvestmentCashLegConsumerTest {

    @Mock
    private InvestmentCashFlowRepository repository;
    @InjectMocks
    private InvestmentCashLegConsumer consumer;

    private static CashLegCommand fund() {
        return new CashLegCommand(1, "CASH_LEG", "evt-1", "FUND", "CHECKING",
                new BigDecimal("100.00"), LocalDate.of(2026, 7, 24));
    }

    @Test
    void persistsNewCashLeg() {
        when(repository.existsById("evt-1")).thenReturn(false);

        consumer.handle(fund());

        ArgumentCaptor<InvestmentCashFlow> captor = ArgumentCaptor.forClass(InvestmentCashFlow.class);
        verify(repository).save(captor.capture());
        InvestmentCashFlow saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-1");
        assertThat(saved.getType()).isEqualTo(CashLegType.FUND);
        assertThat(saved.getAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(saved.getFlowDate()).isEqualTo(LocalDate.of(2026, 7, 24));
    }

    @Test
    void skipsAlreadyAppliedCashLeg() {
        when(repository.existsById("evt-1")).thenReturn(true);

        consumer.handle(fund());

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
