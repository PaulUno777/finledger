package com.pauluno.finledger.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pauluno.finledger.application.port.out.EventPublisher;
import com.pauluno.finledger.infrastructure.fraud.AsyncFraudHandler;

@Configuration
public class EventPublisherConfig {

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    EventPublisher eventPublisher(AsyncFraudHandler asyncFraudHandler) {
        EventPublisher logging = new LoggingEventPublisher();
        return event -> {
            logging.publish(event);
            asyncFraudHandler.onPublished(event);
        };
    }
}
