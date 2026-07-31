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

import com.pauluno.finledger.security.policy.SecurityMode;
import com.pauluno.finledger.security.policy.SecurityModePolicy;
import com.pauluno.finledger.security.policy.SecurityModeViolationException;

/**
 * Fails boot before accepting traffic when a non-enforced mode runs in production (FL-151).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityModeGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityModeGuard.class);

    private final Environment environment;
    private final String modeRaw;
    private final String finledgerEnv;

    public SecurityModeGuard(
            Environment environment,
            @Value("${finledger.security.mode:enforced}") String modeRaw,
            @Value("${finledger.env:local}") String finledgerEnv
    ) {
        this.environment = environment;
        this.modeRaw = modeRaw;
        this.finledgerEnv = finledgerEnv;
    }

    @Override
    public void run(ApplicationArguments args) {
        SecurityMode mode = SecurityMode.parse(modeRaw);
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        try {
            SecurityModePolicy.assertBootAllowed(mode, finledgerEnv, profiles);
        } catch (SecurityModeViolationException ex) {
            log.error(ex.getMessage());
            throw ex;
        }
        if (mode != SecurityMode.ENFORCED) {
            log.warn(
                    "SECURITY MODE={} — not for public production. "
                            + "Set finledger.security.mode=enforced (OIDC/JWT) before deploying.",
                    mode.configValue());
        } else {
            log.info("Security mode=enforced (OIDC/JWT)");
        }
    }
}
