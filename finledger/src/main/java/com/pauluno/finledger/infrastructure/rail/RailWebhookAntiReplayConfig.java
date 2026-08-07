package com.pauluno.finledger.infrastructure.rail;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pauluno.finledger.application.rail.RailWebhookAntiReplay;

@Configuration
public class RailWebhookAntiReplayConfig {

    @Bean
    RailWebhookAntiReplay railWebhookAntiReplay(
            @Value("${finledger.rail.webhook.max-skew-seconds:300}") long maxSkewSeconds
    ) {
        return new RailWebhookAntiReplay(Duration.ofSeconds(maxSkewSeconds), Clock.systemUTC());
    }
}
