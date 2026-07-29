package com.pauluno.finledger.domain.exception;

public final class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
