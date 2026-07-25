package com.financedash.investments.messaging.contract;

import com.financedash.investments.messaging.InvestmentsMessaging;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Value snapshot (service → backend): the full current net value of all OPEN holdings as of
 * {@code asOf}. <b>State snapshot</b>, not a delta — the backend overwrites its shallow copy and
 * ignores any snapshot older than the one it holds, so a lost intermediate self-heals.
 */
public record ValueSnapshot(
        int schemaVersion,
        String type,
        BigDecimal netValue,
        Instant asOf) {

    public static final String TYPE = "VALUE_SNAPSHOT";

    public static ValueSnapshot of(BigDecimal netValue, Instant asOf) {
        return new ValueSnapshot(InvestmentsMessaging.SCHEMA_VERSION, TYPE, netValue, asOf);
    }
}
