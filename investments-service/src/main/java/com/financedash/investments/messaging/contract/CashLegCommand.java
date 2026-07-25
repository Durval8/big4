package com.financedash.investments.messaging.contract;

import com.financedash.investments.messaging.InvestmentsMessaging;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash-leg command (service → backend): "a buy drew {@code amount} from {@code account}" /
 * "a cash-out returned {@code amount} to savings". <b>Incremental</b> — must be applied exactly
 * once, so it carries a stable {@code eventId} the backend dedupes on. {@code date} scopes it
 * into the backend's period metrics ({@code netInvestment}).
 */
public record CashLegCommand(
        int schemaVersion,
        String type,
        String eventId,
        String legType,   // FUND | CASH_OUT
        String account,   // CHECKING | SAVINGS
        BigDecimal amount,
        LocalDate date) {

    public static final String TYPE = "CASH_LEG";

    public static CashLegCommand of(String eventId, String legType, String account,
                                    BigDecimal amount, LocalDate date) {
        return new CashLegCommand(
                InvestmentsMessaging.SCHEMA_VERSION, TYPE, eventId, legType, account, amount, date);
    }
}
