package com.financedash.dto;

import com.financedash.domain.Category;
import java.math.BigDecimal;

/**
 * One category's expense total for the analytics window, alongside its total for the immediately
 * preceding window of equal length (null when no prior period exists — see
 * {@code AnalyticsService}).
 */
public record CategoryTotal(Category category, BigDecimal amount, BigDecimal previousAmount) {}
