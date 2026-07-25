package com.financedash.investments.provider;

/**
 * The provider does not recognize the symbol (not a transient error). Not retryable: the holding
 * is flagged {@code UNRESOLVED}, excluded from API fetches, and priced manually.
 */
public class SymbolNotFoundException extends ProviderException {
    public SymbolNotFoundException(String symbol) {
        super("Symbol not recognized by provider: " + symbol);
    }
}
