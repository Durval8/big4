package com.financedash.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedash.domain.AccountType;
import com.financedash.domain.CashLegType;
import com.financedash.domain.InvestmentCashFlow;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.repository.InvestmentCashFlowRepository;
import com.financedash.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cash-leg consumer: records new legs as both a projection row and a ledger row, ignores
 * redeliveries (idempotent by eventId).
 */
@ExtendWith(MockitoExtension.class)
class InvestmentCashLegConsumerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 24);

    @Mock
    private InvestmentCashFlowRepository repository;
    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private InvestmentCashLegConsumer consumer;

    private static CashLegCommand fund() {
        return new CashLegCommand(1, "CASH_LEG", "evt-1", "FUND", "CHECKING",
                new BigDecimal("100.00"), DATE, "AAPL");
    }

    private static CashLegCommand cashOut() {
        return new CashLegCommand(1, "CASH_LEG", "evt-2", "CASH_OUT", "SAVINGS",
                new BigDecimal("250.00"), DATE, "AAPL");
    }

    private Transaction captureLedgerRow() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
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
        assertThat(saved.getFlowDate()).isEqualTo(DATE);
    }

    @Test
    void fundWritesATransferFromTheFundingAccountToInvesting() {
        when(repository.existsById("evt-1")).thenReturn(false);

        consumer.handle(fund());

        Transaction row = captureLedgerRow();
        assertThat(row.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(row.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(row.getLinkedAccountType()).isEqualTo(AccountType.INVESTING);
        assertThat(row.getAmount()).isEqualByComparingTo("100.00");
        assertThat(row.getTransactionDate()).isEqualTo(DATE);
        assertThat(row.getCategory()).isNull();          // TRANSFER forbids a category
        assertThat(row.getDescription()).isEqualTo("Bought AAPL");
        assertThat(row.getSourceEventId()).isEqualTo("evt-1");
        assertThat(row.isSystemGenerated()).isTrue();
    }

    @Test
    void cashOutWritesATransferFromInvestingToSavings() {
        when(repository.existsById("evt-2")).thenReturn(false);

        consumer.handle(cashOut());

        Transaction row = captureLedgerRow();
        assertThat(row.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(row.getAccountType()).isEqualTo(AccountType.INVESTING);
        assertThat(row.getLinkedAccountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(row.getAmount()).isEqualByComparingTo("250.00");
        assertThat(row.getDescription()).isEqualTo("Cashed out AAPL");
        assertThat(row.getSourceEventId()).isEqualTo("evt-2");
    }

    @Test
    void fallsBackToAGenericDescriptionWhenTheSymbolIsAbsent() {
        // stockSymbol was added to the contract after the first release — a message enqueued by an
        // older service build arrives without it and must still apply.
        when(repository.existsById("evt-1")).thenReturn(false);

        consumer.handle(new CashLegCommand(1, "CASH_LEG", "evt-1", "FUND", "CHECKING",
                new BigDecimal("100.00"), DATE, null));

        assertThat(captureLedgerRow().getDescription()).isEqualTo("Investment funding");
    }

    @Test
    void skipsAlreadyAppliedCashLeg() {
        when(repository.existsById("evt-1")).thenReturn(true);

        consumer.handle(fund());

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
