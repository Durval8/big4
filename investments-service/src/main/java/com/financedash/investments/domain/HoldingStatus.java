package com.financedash.investments.domain;

/** Lifecycle of a holding. A fully cashed-out holding is kept as history. */
public enum HoldingStatus {
    OPEN,
    CASHED_OUT
}
