package com.financedash.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Consumer-side mirror of the investments service's value snapshot (full current investing value
 * as of {@code asOf}). Applied as a last-write-wins overwrite of the backend's shallow copy.
 */
public record ValueSnapshot(
        int schemaVersion,
        String type,
        BigDecimal netValue,
        Instant asOf) {
}
