package com.pauluno.finledger.infrastructure.security;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.security.policy.RuntimeSecurityPolicy;
import com.pauluno.finledger.security.policy.RuntimeSecurityViolationException;

/**
 * Fails boot when sandbox runs in production or profiles conflict (ADR-016).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RuntimeSecurityGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSecurityGuard.class);

    private final Environment environment;
    private final String finledgerEnv;
    private final String issuerRaw;

    public RuntimeSecurityGuard(
            Environment environment,
            @Value("${finledger.env:local}") String finledgerEnv,
            @Value("${finledger.security.issuer:external}") String issuerRaw
    ) {
        this.environment = environment;
        this.finledgerEnv = finledgerEnv;
        this.issuerRaw = issuerRaw;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        String issuer;
        try {
            issuer = RuntimeSecurityPolicy.normalizeIssuer(issuerRaw);
            RuntimeSecurityPolicy.assertProfilesExclusive(profiles);
            RuntimeSecurityPolicy.assertSandboxProfileAllowed(finledgerEnv, profiles);
        } catch (RuntimeSecurityViolationException | IllegalArgumentException ex) {
            log.error(ex.getMessage());
            if (ex instanceof RuntimeSecurityViolationException violation) {
                throw violation;
            }
            throw new RuntimeSecurityViolationException(ex.getMessage());
        }
        if (RuntimeSecurityPolicy.hasSandboxProfile(profiles)) {
            log.info("Runtime profile=sandbox issuer={} (in-box JWT — ADR-016)", issuer);
        } else {
            log.info("Runtime profile=normal issuer={} (JWT always on — ADR-016)", issuer);
        }
    }
}
