package com.pauluno.finledger.application.exception;

/**
 * Platform bootstrap already consumed — endpoint permanently dead (HTTP 410).
 */
public final class PlatformBootstrapAlreadyClaimedException extends RuntimeException {

    public PlatformBootstrapAlreadyClaimedException() {
        super("Platform bootstrap has already been claimed");
    }
}
