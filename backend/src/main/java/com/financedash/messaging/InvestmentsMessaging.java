package com.financedash.messaging;

/**
 * Broker names shared with the investments service. The service <b>owns</b> the canonical
 * definitions (see its {@code InvestmentsMessaging}); this is the backend's mirror. Kept in sync by
 * the contract test {@code InvestmentMessageContractTest}. The backend only consumes.
 */
public final class InvestmentsMessaging {

    public static final String INVESTMENTS_EXCHANGE = "investments.exchange";
    public static final String CASH_LEG_ROUTING_KEY = "investment.cashleg";
    public static final String VALUE_ROUTING_KEY = "investment.value";
    public static final String BACKEND_CASH_LEG_QUEUE = "backend.investment.cashleg";
    public static final String BACKEND_VALUE_QUEUE = "backend.investment.value";

    private InvestmentsMessaging() {}
}
