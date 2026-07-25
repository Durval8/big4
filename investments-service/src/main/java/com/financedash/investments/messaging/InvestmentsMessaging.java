package com.financedash.investments.messaging;

/**
 * Canonical broker names and contract version — owned by the investments service (the backend
 * mirrors these). Two service→backend streams on one exchange, distinguished by routing key:
 * cash-leg commands (incremental, must-not-lose) and value snapshots (state, self-healing).
 * A separate intra-service exchange carries the price-refresh work queue.
 */
public final class InvestmentsMessaging {

    /** Bumped only on a breaking payload change; every message carries it. */
    public static final int SCHEMA_VERSION = 1;

    // Service → backend.
    public static final String INVESTMENTS_EXCHANGE = "investments.exchange";
    public static final String CASH_LEG_ROUTING_KEY = "investment.cashleg";
    public static final String VALUE_ROUTING_KEY = "investment.value";
    public static final String BACKEND_CASH_LEG_QUEUE = "backend.investment.cashleg";
    public static final String BACKEND_VALUE_QUEUE = "backend.investment.value";

    // Intra-service price-refresh work queue.
    public static final String PRICE_EXCHANGE = "price.exchange";
    public static final String PRICE_REFRESH_ROUTING_KEY = "price.refresh";
    public static final String PRICE_REFRESH_QUEUE = "price.refresh";
    public static final String PRICE_REFRESH_DLQ = "price.refresh.dlq";
    public static final String PRICE_DLX = "price.dlx";

    // Intra-service news-refresh trigger (best-effort; no DLQ — a lost/failed rebuild self-heals
    // at the next 4h tick).
    public static final String NEWS_EXCHANGE = "news.exchange";
    public static final String NEWS_REFRESH_ROUTING_KEY = "news.refresh";
    public static final String NEWS_REFRESH_QUEUE = "news.refresh";

    private InvestmentsMessaging() {}
}
