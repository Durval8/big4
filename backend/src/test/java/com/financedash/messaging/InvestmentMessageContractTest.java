package com.financedash.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Contract guard for the "investments service owns the message shape" decision. Because the two
 * services keep physically separate record definitions, a renamed field would deserialize to
 * {@code null} silently. These fixtures are the service's exact JSON (field names, types); every
 * field must bind. If the service renames a field, update it here <b>and</b> in the service together.
 */
class InvestmentMessageContractTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void cashLegCommandBindsEveryField() throws Exception {
        String json = """
                {"schemaVersion":1,"type":"CASH_LEG","eventId":"evt-1","legType":"FUND",
                 "account":"CHECKING","amount":100.00,"date":"2026-07-24"}
                """;

        CashLegCommand cmd = mapper.readValue(json, CashLegCommand.class);

        assertThat(cmd.schemaVersion()).isEqualTo(1);
        assertThat(cmd.type()).isEqualTo("CASH_LEG");
        assertThat(cmd.eventId()).isEqualTo("evt-1");
        assertThat(cmd.legType()).isEqualTo("FUND");
        assertThat(cmd.account()).isEqualTo("CHECKING");
        assertThat(cmd.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(cmd.date()).isEqualTo(LocalDate.of(2026, 7, 24));
    }

    @Test
    void valueSnapshotBindsEveryField() throws Exception {
        String json = """
                {"schemaVersion":1,"type":"VALUE_SNAPSHOT","netValue":1234.56,"asOf":"2026-07-24T12:00:00Z"}
                """;

        ValueSnapshot snapshot = mapper.readValue(json, ValueSnapshot.class);

        assertThat(snapshot.schemaVersion()).isEqualTo(1);
        assertThat(snapshot.type()).isEqualTo("VALUE_SNAPSHOT");
        assertThat(snapshot.netValue()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(snapshot.asOf()).isEqualTo(Instant.parse("2026-07-24T12:00:00Z"));
    }
}
