package com.financedash.investments.provider;

import java.math.BigDecimal;
import java.time.Instant;

/** A price quote for a symbol: price per share as of an instant. */
public record Quote(BigDecimal price, Instant asOf) {}
