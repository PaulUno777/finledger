package com.pauluno.finledger.sdkref;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;

/**
 * Exponential backoff with full jitter for outbound HTTP.
 * Retries connection failures and {@code 5xx}; optionally {@code 408}/{@code 429}.
 * Never retries other {@code 4xx}.
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final boolean retryOn408Or429;
    private final LongConsumer sleeper;

    public RetryPolicy(
            int maxAttempts,
            long initialBackoffMillis,
            long maxBackoffMillis,
            boolean retryOn408Or429,
            LongConsumer sleeper
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.retryOn408Or429 = retryOn408Or429;
        this.sleeper = Objects.requireNonNullElse(sleeper, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("retry sleep interrupted", ex);
            }
        });
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, 100L, 2_000L, true, null);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean shouldRetry(int attemptIndexZeroBased, int statusCode, boolean connectionFailure) {
        if (attemptIndexZeroBased + 1 >= maxAttempts) {
            return false;
        }
        if (connectionFailure) {
            return true;
        }
        if (statusCode >= 500 && statusCode <= 599) {
            return true;
        }
        if (retryOn408Or429 && (statusCode == 408 || statusCode == 429)) {
            return true;
        }
        return false;
    }

    /** Sleeps a jittered backoff for the given zero-based attempt that just failed. */
    public void backoff(int attemptIndexZeroBased) {
        long exp = initialBackoffMillis * (1L << Math.min(attemptIndexZeroBased, 16));
        long capped = Math.min(exp, maxBackoffMillis);
        long sleep = capped <= 0 ? 0 : ThreadLocalRandom.current().nextLong(capped + 1);
        sleeper.accept(sleep);
    }
}
