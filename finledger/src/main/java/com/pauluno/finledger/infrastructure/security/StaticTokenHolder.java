package com.pauluno.finledger.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.SecretsProvider;

/**
 * Resolves {@code FINLEDGER_STATIC_TOKEN}, generating one in non-prod when unset.
 */
@Component
public class StaticTokenHolder {

    private final AtomicReference<String> generated = new AtomicReference<>();

    public String resolve(SecretsProvider secretsProvider) {
        return secretsProvider.get(LedgerAuthorities.STATIC_TOKEN_SECRET_KEY)
                .filter(s -> !s.isBlank())
                .orElseGet(this::generateOnce);
    }

    public String peekGeneratedOrNull() {
        return generated.get();
    }

    private String generateOnce() {
        return generated.updateAndGet(existing -> {
            if (existing != null) {
                return existing;
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        });
    }
}
