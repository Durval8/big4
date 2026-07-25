package com.financedash.investments.exception;

/**
 * The price provider is transiently unavailable during a buy. The buy is blocked rather than
 * creating a shareless holding (pricing decision #3). → 503.
 */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) {
        super(message);
    }
}
