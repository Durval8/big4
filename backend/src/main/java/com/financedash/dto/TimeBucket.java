package com.financedash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One bucket of the analytics time series — gap-filled, so a zero-activity bucket still appears. */
public record TimeBucket(LocalDate start, BigDecimal income, BigDecimal expense) {}
