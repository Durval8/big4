package com.financedash.investments.exception;

/** A request that breaks an investment rule (bad account, over-cash-out, unpriceable buy). → 400. */
public class InvalidInvestmentException extends RuntimeException {
    public InvalidInvestmentException(String message) {
        super(message);
    }
}
