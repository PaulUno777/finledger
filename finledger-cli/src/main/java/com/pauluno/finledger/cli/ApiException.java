package com.pauluno.finledger.cli;

/**
 * Non-2xx HTTP response from the FinLedger API.
 */
public final class ApiException extends Exception {

    private final int statusCode;
    private final String bodySnippet;

    public ApiException(int statusCode, String bodySnippet) {
        super("HTTP " + statusCode + (bodySnippet == null || bodySnippet.isBlank() ? "" : ": " + bodySnippet));
        this.statusCode = statusCode;
        this.bodySnippet = bodySnippet;
    }

    public int statusCode() {
        return statusCode;
    }

    public String bodySnippet() {
        return bodySnippet;
    }
}
