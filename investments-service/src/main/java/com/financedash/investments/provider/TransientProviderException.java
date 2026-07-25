package com.financedash.investments.provider;

/**
 * Transient provider failure (429/5xx/timeout/rate-limit-exhausted). Retryable: the price job
 * retries then dead-letters (keeping last-known price, {@code STALE}); a buy is blocked.
 */
public class TransientProviderException extends ProviderException {
    public TransientProviderException(String message) { super(message); }
    public TransientProviderException(String message, Throwable cause) { super(message, cause); }
}
