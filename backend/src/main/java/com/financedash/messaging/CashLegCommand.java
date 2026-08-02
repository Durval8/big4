package com.financedash.messaging;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Consumer-side mirror of the investments service's cash-leg command. Field names must match the
 * service's record exactly (JSON binds by name) — the contract test guards this. {@code eventId}
 * is the idempotency key; {@code date} scopes the leg into period metrics.
 *
 * <p>{@code stockSymbol} may be <b>null</b> for a message enqueued before that field was added;
 * it only labels the generated ledger row, so the consumer falls back to a generic description.
 */
public record CashLegCommand(
        int schemaVersion,
        String type,
        String eventId,
        String legType,     // FUND | CASH_OUT
        String account,     // CHECKING | SAVINGS
        BigDecimal amount,
        LocalDate date,
        String stockSymbol) {
}
