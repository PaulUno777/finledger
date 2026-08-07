package com.pauluno.finledger.application.usecase;

import java.util.function.Supplier;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bounded optimistic-lock retries with a <strong>new</strong> DB transaction per attempt
 * and backoff <strong>outside</strong> any transaction (plan §8.3 / FL-170).
 */
@Component
public class OptimisticLockRetry {

    private static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate transactionTemplate;

    public OptimisticLockRetry(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> attempt) {
        OptimisticLockingFailureException last = null;
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                return transactionTemplate.execute(status -> attempt.get());
            } catch (OptimisticLockingFailureException ex) {
                last = ex;
                if (i == MAX_ATTEMPTS) {
                    break;
                }
                sleepBackoff(i);
            }
        }
        throw last;
    }

    /**
     * Unit-test helper: retry/backoff without a real transaction manager.
     */
    public static <T> T executeDirect(Supplier<T> attempt) {
        OptimisticLockingFailureException last = null;
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                return attempt.get();
            } catch (OptimisticLockingFailureException ex) {
                last = ex;
                if (i == MAX_ATTEMPTS) {
                    break;
                }
                sleepBackoff(i);
            }
        }
        throw last;
    }

    /** Test double that delegates to {@link #executeDirect}. */
    public static OptimisticLockRetry forUnitTests() {
        return new OptimisticLockRetry(new PlatformTransactionManager() {
            @Override
            public org.springframework.transaction.TransactionStatus getTransaction(
                    org.springframework.transaction.TransactionDefinition definition) {
                return new org.springframework.transaction.support.SimpleTransactionStatus();
            }

            @Override
            public void commit(org.springframework.transaction.TransactionStatus status) {
            }

            @Override
            public void rollback(org.springframework.transaction.TransactionStatus status) {
            }
        }) {
            @Override
            public <T> T execute(Supplier<T> attempt) {
                return executeDirect(attempt);
            }
        };
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(10L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Optimistic-lock backoff interrupted", ie);
        }
    }
}
