package com.pauluno.finledger.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.SplitRuleSet;

public interface SplitRuleSetRepository {

    SplitRuleSet save(UUID tenantId, SplitRuleSet ruleSet);

    Optional<SplitRuleSet> findByTenantAndKey(UUID tenantId, String ruleSetKey);
}
