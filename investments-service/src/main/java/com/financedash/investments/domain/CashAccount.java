package com.financedash.investments.domain;

/**
 * The backend cash accounts a buy can draw from / a cash-out returns to. Deliberately a small,
 * service-local enum rather than a dependency on the backend's {@code AccountType}: the two
 * services agree on these names only through the message contract.
 */
public enum CashAccount {
    CHECKING,
    SAVINGS
}
