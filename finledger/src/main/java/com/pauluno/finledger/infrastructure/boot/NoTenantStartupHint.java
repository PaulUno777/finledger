package com.pauluno.finledger.infrastructure.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantRepository;

/**
 * Plan §18.1 — log a provisioning hint when the database has zero tenants.
 * Never blocks health; failures to count are logged and ignored.
 */
@Component
public class NoTenantStartupHint implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NoTenantStartupHint.class);

    private final SpringDataTenantRepository tenantRepository;

    public NoTenantStartupHint(SpringDataTenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (tenantRepository.count() == 0L) {
                log.info(
                        "No tenants configured — create one with finledger-cli tenant create or POST /api/v1/tenants");
            }
        } catch (Exception ex) {
            log.warn("Could not check tenant count at startup (health unaffected): {}", ex.getMessage());
        }
    }
}
