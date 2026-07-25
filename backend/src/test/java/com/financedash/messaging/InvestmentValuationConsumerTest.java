package com.financedash.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedash.domain.InvestmentValuation;
import com.financedash.repository.InvestmentValuationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Valuation consumer: last-write-wins by asOf — reject strictly older, accept ties and newer. */
@ExtendWith(MockitoExtension.class)
class InvestmentValuationConsumerTest {

    private static final Instant T = Instant.parse("2026-07-24T12:00:00Z");

    @Mock
    private InvestmentValuationRepository repository;
    @InjectMocks
    private InvestmentValuationConsumer consumer;

    private static ValueSnapshot snapshot(String netValue, Instant asOf) {
        return new ValueSnapshot(1, "VALUE_SNAPSHOT", new BigDecimal(netValue), asOf);
    }

    @Test
    void createsSingletonWhenNoneExists() {
        when(repository.findById(InvestmentValuation.SINGLETON_ID)).thenReturn(Optional.empty());

        consumer.handle(snapshot("500.00", T));

        ArgumentCaptor<InvestmentValuation> captor = ArgumentCaptor.forClass(InvestmentValuation.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNetValue()).isEqualByComparingTo("500.00");
        assertThat(captor.getValue().getAsOf()).isEqualTo(T);
    }

    @Test
    void appliesNewerSnapshot() {
        InvestmentValuation existing = new InvestmentValuation(new BigDecimal("500.00"), T);
        when(repository.findById(InvestmentValuation.SINGLETON_ID)).thenReturn(Optional.of(existing));

        consumer.handle(snapshot("700.00", T.plusSeconds(60)));

        verify(repository).save(existing);
        assertThat(existing.getNetValue()).isEqualByComparingTo("700.00");
        assertThat(existing.getAsOf()).isEqualTo(T.plusSeconds(60));
    }

    @Test
    void acceptsTieAsOf() {
        InvestmentValuation existing = new InvestmentValuation(new BigDecimal("500.00"), T);
        when(repository.findById(InvestmentValuation.SINGLETON_ID)).thenReturn(Optional.of(existing));

        consumer.handle(snapshot("650.00", T)); // same instant → later arrival wins

        verify(repository).save(existing);
        assertThat(existing.getNetValue()).isEqualByComparingTo("650.00");
    }

    @Test
    void ignoresStrictlyOlderSnapshot() {
        InvestmentValuation existing = new InvestmentValuation(new BigDecimal("500.00"), T);
        when(repository.findById(InvestmentValuation.SINGLETON_ID)).thenReturn(Optional.of(existing));

        consumer.handle(snapshot("100.00", T.minusSeconds(60)));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(existing.getNetValue()).isEqualByComparingTo("500.00"); // unchanged
    }
}
