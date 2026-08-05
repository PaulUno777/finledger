package com.pauluno.finledger.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.CreateTenantCommand;
import com.pauluno.finledger.application.dto.CreateTenantResult;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.CreateTenantUseCase;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;

@Service
public class CreateTenantService implements CreateTenantUseCase {

    private final TenantRepository tenantRepository;

    public CreateTenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE_TENANT", resourceType = "TENANT")
    public CreateTenantResult execute(CreateTenantCommand command) {
        TenantType type = TenantType.valueOf(command.type());
        UUID parentId = command.parentTenantId();

        if (type == TenantType.SUB_MERCHANT) {
            if (parentId == null) {
                throw new BusinessRuleException(
                        "INVALID_TENANT_HIERARCHY",
                        "SUB_MERCHANT requires parentTenantId");
            }
            Tenant parent = tenantRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent tenant not found: " + parentId));
            if (parent.type() != TenantType.AGGREGATOR) {
                throw new BusinessRuleException(
                        "INVALID_TENANT_HIERARCHY",
                        "SUB_MERCHANT parent must be an AGGREGATOR");
            }
        } else if (parentId != null) {
            throw new BusinessRuleException(
                    "INVALID_TENANT_HIERARCHY",
                    type + " must not have a parent");
        }

        UUID tenantId = command.id() == null ? UUID.randomUUID() : command.id();
        if (command.id() != null && tenantRepository.findById(tenantId).isPresent()) {
            throw new BusinessRuleException(
                    "TENANT_ID_CONFLICT",
                    "Tenant already exists: " + tenantId);
        }

        Tenant tenant = new Tenant(tenantId, type, parentId, command.name());
        Tenant saved = tenantRepository.save(tenant);

        List<UUID> ancestors = new ArrayList<>();
        ancestors.add(saved.id());
        if (parentId != null) {
            ancestors.addAll(tenantRepository.findAncestorIds(parentId));
        }
        tenantRepository.replaceAncestry(saved.id(), ancestors);

        return new CreateTenantResult(
                saved.id(),
                saved.name(),
                saved.type().name(),
                saved.parentTenantId()
        );
    }
}
