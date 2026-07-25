package com.financedash.investments.domain;

/** A buy ({@code FUND}) draws cash from an account; a sell ({@code CASH_OUT}) returns proceeds to savings. */
public enum InvestmentEventType {
    FUND,
    CASH_OUT
}
