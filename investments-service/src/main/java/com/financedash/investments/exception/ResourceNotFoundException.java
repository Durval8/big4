package com.financedash.investments.exception;

/** A holding (or other resource) was not found. → 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
