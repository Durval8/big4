package com.financedash.investments.provider;

/** Base for stock-price provider failures. */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) { super(message); }
    public ProviderException(String message, Throwable cause) { super(message, cause); }
}
