package com.pauluno.finledger.infrastructure.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.tenant.TenantContext;

/**
 * Schedules outbox publishing with RLS bypass so the poller can see all tenants' PENDING rows.
 */
@Component
public class OutboxPollScheduler {

    private final OutboxPoller outboxPoller;

    public OutboxPollScheduler(OutboxPoller outboxPoller) {
        this.outboxPoller = outboxPoller;
    }

    @Scheduled(fixedDelayString = "${finledger.outbox.poll-interval-ms:1000}")
    public void run() {
        TenantContext.enableBypass();
        try {
            outboxPoller.poll();
        } finally {
            TenantContext.clear();
        }
    }
}
