package com.financedash.investments.domain;

/** Freshness/validity of a holding's {@code latestPrice}. */
public enum PriceStatus {
    /** Price came from the provider on the last refresh. */
    OK,
    /** Provider is persistently failing for this symbol; last-known price is kept (shown with a warning). */
    STALE,
    /** Symbol is not recognized by the provider; it is never fetched via the API and is priced manually. */
    UNRESOLVED
}
