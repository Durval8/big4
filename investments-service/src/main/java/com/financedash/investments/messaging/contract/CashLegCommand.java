package com.financedash.investments.messaging.contract;

import com.financedash.investments.messaging.InvestmentsMessaging;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash-leg command (service → backend): "a buy drew {@code amount} from {@code account}" /
 * "a cash-out returned {@code amount} to savings". <b>Incremental</b> — must be applied exactly
 * once, so it carries a stable {@code eventId} the backend dedupes on. {@code date} scopes it
 * into the backend's period metrics ({@code netInvestment}).
 *
 * <p>{@code stockSymbol} is additive and purely descriptive — the backend uses it to label the
 * ledger row it writes ("Bought AAPL"). It does not affect any balance or metric, so it was added
 * without bumping {@code schemaVersion}: a consumer that receives it as null (a message enqueued
 * before this field existed) falls back to a generic description.
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

    public static final String TYPE = "CASH_LEG";

    public static CashLegCommand of(String eventId, String legType, String account,
                                    BigDecimal amount, LocalDate date, String stockSymbol) {
        return new CashLegCommand(InvestmentsMessaging.SCHEMA_VERSION, TYPE, eventId, legType,
                account, amount, date, stockSymbol);
    }
}
