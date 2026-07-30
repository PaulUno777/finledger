package com.pauluno.finledger.application.port.out;

import java.util.Optional;

/**
 * Resolves secret values without hard-coding them in config files (plan §2.3 / §11).
 */
public interface SecretsProvider {

    Optional<String> get(String key);
}
