package com.pauluno.finledger.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.security.policy.SecurityMode;

/**
 * Periodic reminder when running outside {@code enforced} mode (FL-151).
 */
@Component
public class SecurityModeWarnScheduler {

    private static final Logger log = LoggerFactory.getLogger(SecurityModeWarnScheduler.class);

    private final SecurityMode mode;

    public SecurityModeWarnScheduler(@Value("${finledger.security.mode:enforced}") String modeRaw) {
        this.mode = SecurityMode.parse(modeRaw);
    }

    @Scheduled(fixedDelayString = "${finledger.security.warn-interval-ms:300000}")
    public void warnIfInsecure() {
        if (mode == SecurityMode.ENFORCED) {
            return;
        }
        log.warn(
                "SECURITY MODE={} is still active — use only for local eval/CI. "
                        + "Production requires finledger.security.mode=enforced.",
                mode.configValue());
    }
}
