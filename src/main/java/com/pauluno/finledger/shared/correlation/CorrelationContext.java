package com.pauluno.finledger.shared.correlation;

import org.slf4j.MDC;

public final class CorrelationContext {

    private CorrelationContext() {
    }

    public static String getId() {
        return MDC.get("correlationId");
    }

}
