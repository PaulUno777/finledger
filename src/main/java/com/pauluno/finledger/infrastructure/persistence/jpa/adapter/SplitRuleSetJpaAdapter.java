package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.application.port.out.SplitRuleSetRepository;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.SplitRule;
import com.pauluno.finledger.domain.model.SplitRuleSet;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantSplitRuleSetEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantSplitRuleSetId;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantSplitRuleSetRepository;

@Component
public class SplitRuleSetJpaAdapter implements SplitRuleSetRepository {

    private final SpringDataTenantSplitRuleSetRepository repository;
    private final ObjectMapper objectMapper;

    public SplitRuleSetJpaAdapter(SpringDataTenantSplitRuleSetRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public SplitRuleSet save(UUID tenantId, SplitRuleSet ruleSet) {
        TenantSplitRuleSetId id = new TenantSplitRuleSetId(tenantId, ruleSet.key());
        TenantSplitRuleSetEntity entity = repository.findById(id).orElseGet(TenantSplitRuleSetEntity::new);
        entity.setTenantId(tenantId);
        entity.setRuleSetKey(ruleSet.key());
        entity.setRulesJson(serializeRules(ruleSet.rules()));
        entity.setRemainderTarget(ruleSet.remainderTarget().name());
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SplitRuleSet> findByTenantAndKey(UUID tenantId, String ruleSetKey) {
        return repository.findById(new TenantSplitRuleSetId(tenantId, ruleSetKey))
                .map(SplitRuleSetJpaAdapter::toDomain);
    }

    private String serializeRules(List<SplitRule> rules) {
        try {
            List<RuleJson> payload = new ArrayList<>();
            for (SplitRule rule : rules) {
                payload.add(new RuleJson(rule.targetAccountType().name(), rule.percentage().toPlainString()));
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize split rules", e);
        }
    }

    private static SplitRuleSet toDomain(TenantSplitRuleSetEntity entity) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<RuleJson> rules = mapper.readValue(
                    entity.getRulesJson(),
                    new TypeReference<>() {
                    });
            List<SplitRule> domainRules = rules.stream()
                    .map(r -> new SplitRule(
                            AccountType.valueOf(r.targetAccountType()),
                            new BigDecimal(r.percentage())))
                    .toList();
            return new SplitRuleSet(
                    entity.getRuleSetKey(),
                    domainRules,
                    AccountType.valueOf(entity.getRemainderTarget())
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize split rules", e);
        }
    }

    private record RuleJson(String targetAccountType, String percentage) {
    }
}
