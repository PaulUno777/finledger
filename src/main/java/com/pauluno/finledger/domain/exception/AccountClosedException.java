package com.pauluno.finledger.domain.exception;

public final class AccountClosedException extends DomainException {

    public AccountClosedException(String message) {
        super(message);
    }
}
