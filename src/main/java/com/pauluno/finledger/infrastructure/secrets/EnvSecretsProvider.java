package com.pauluno.finledger.infrastructure.secrets;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.SecretsProvider;

import jakarta.annotation.PostConstruct;

/**
 * Dev-oriented default: read secrets from environment / system properties.
 * Prefer Vault or a cloud secrets manager adapter in production deployments.
 */
@Component
public class EnvSecretsProvider implements SecretsProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvSecretsProvider.class);

    @PostConstruct
    void warnDevDefault() {
        log.warn(
                "SecretsProvider is using environment/system properties (dev-oriented default). "
                        + "Do not rely on this alone for production — swap in a Vault/KMS adapter."
        );
    }

    @Override
    public Optional<String> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv);
        }
        String fromProp = System.getProperty(key);
        if (fromProp != null && !fromProp.isBlank()) {
            return Optional.of(fromProp);
        }
        return Optional.empty();
    }
}
