package com.pauluno.finledger.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.dto.CreateTenantCommand;
import com.pauluno.finledger.application.dto.CreateTenantResult;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;

@Tag("unit")
class CreateTenantServiceTest {

    private InMemoryTenantRepository repository;
    private CreateTenantService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTenantRepository();
        service = new CreateTenantService(repository);
    }

    @Test
    void should_create_standalone_with_self_ancestry() {
        CreateTenantResult result = service.execute(
                new CreateTenantCommand("Acme", "STANDALONE", null));

        assertThat(result.type()).isEqualTo("STANDALONE");
        assertThat(result.parentTenantId()).isNull();
        assertThat(repository.findAncestorIds(result.tenantId()))
                .containsExactly(result.tenantId());
    }

    @Test
    void should_create_aggregator_and_sub_merchant_with_copied_ancestry() {
        CreateTenantResult aggregator = service.execute(
                new CreateTenantCommand("Agg", "AGGREGATOR", null));
        CreateTenantResult sub = service.execute(
                new CreateTenantCommand("Shop", "SUB_MERCHANT", aggregator.tenantId()));

        assertThat(sub.parentTenantId()).isEqualTo(aggregator.tenantId());
        assertThat(repository.findAncestorIds(sub.tenantId()))
                .containsExactlyInAnyOrder(sub.tenantId(), aggregator.tenantId());
        assertThat(repository.findDescendantIds(aggregator.tenantId()))
                .containsExactlyInAnyOrder(aggregator.tenantId(), sub.tenantId());
    }

    @Test
    void should_reject_sub_merchant_without_parent() {
        assertThatThrownBy(() -> service.execute(
                new CreateTenantCommand("Shop", "SUB_MERCHANT", null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).code())
                .isEqualTo("INVALID_TENANT_HIERARCHY");
    }

    @Test
    void should_reject_sub_merchant_under_standalone() {
        CreateTenantResult standalone = service.execute(
                new CreateTenantCommand("Solo", "STANDALONE", null));

        assertThatThrownBy(() -> service.execute(
                new CreateTenantCommand("Shop", "SUB_MERCHANT", standalone.tenantId())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).code())
                .isEqualTo("INVALID_TENANT_HIERARCHY");
    }

    @Test
    void should_reject_parent_on_standalone() {
        UUID fakeParent = UUID.randomUUID();
        assertThatThrownBy(() -> service.execute(
                new CreateTenantCommand("Solo", "STANDALONE", fakeParent)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).code())
                .isEqualTo("INVALID_TENANT_HIERARCHY");
    }

    @Test
    void should_reject_missing_parent() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.execute(
                new CreateTenantCommand("Shop", "SUB_MERCHANT", missing)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_create_with_client_supplied_id() {
        UUID chosen = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        CreateTenantResult result = service.execute(
                new CreateTenantCommand("Chosen", "STANDALONE", null, chosen));
        assertThat(result.tenantId()).isEqualTo(chosen);
    }

    @Test
    void should_conflict_when_client_supplied_id_exists() {
        UUID chosen = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        service.execute(new CreateTenantCommand("First", "STANDALONE", null, chosen));
        assertThatThrownBy(() -> service.execute(
                new CreateTenantCommand("Second", "STANDALONE", null, chosen)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).code())
                .isEqualTo("TENANT_ID_CONFLICT");
    }

    private static final class InMemoryTenantRepository implements TenantRepository {
        private final Map<UUID, Tenant> tenants = new ConcurrentHashMap<>();
        private final Map<UUID, LinkedHashSet<UUID>> ancestorsByDescendant = new ConcurrentHashMap<>();

        @Override
        public Tenant save(Tenant tenant) {
            tenants.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(UUID id) {
            return Optional.ofNullable(tenants.get(id));
        }

        @Override
        public void replaceAncestry(UUID tenantId, List<UUID> ancestorIdsIncludingSelf) {
            LinkedHashSet<UUID> unique = new LinkedHashSet<>(ancestorIdsIncludingSelf);
            unique.add(tenantId);
            ancestorsByDescendant.put(tenantId, unique);
        }

        @Override
        public List<UUID> findAncestorIds(UUID tenantId) {
            return new ArrayList<>(ancestorsByDescendant.getOrDefault(tenantId, new LinkedHashSet<>()));
        }

        @Override
        public List<UUID> findDescendantIds(UUID tenantId) {
            List<UUID> result = new ArrayList<>();
            for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : ancestorsByDescendant.entrySet()) {
                if (entry.getValue().contains(tenantId)) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }
    }
}
