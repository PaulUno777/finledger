package com.pauluno.finledger.infrastructure.messaging;

import java.util.concurrent.Executor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import com.pauluno.finledger.application.port.out.EventPublisher;
import com.pauluno.finledger.infrastructure.fraud.AsyncFraudHandler;

@Configuration
@EnableAsync
public class EventPublisherConfig {

    public static final String FRAUD_ASYNC_EXECUTOR = "fraudAsyncExecutor";

    @Bean(name = FRAUD_ASYNC_EXECUTOR)
    Executor fraudAsyncExecutor() {
        return new VirtualThreadTaskExecutor("fraud-async-");
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    EventPublisher eventPublisher(AsyncFraudHandler asyncFraudHandler) {
        EventPublisher logging = new LoggingEventPublisher();
        return event -> {
            logging.publish(event);
            // Off the outbox poller TX thread (FL-170 / ADR-011)
            asyncFraudHandler.onPublishedAsync(event);
        };
    }
}
