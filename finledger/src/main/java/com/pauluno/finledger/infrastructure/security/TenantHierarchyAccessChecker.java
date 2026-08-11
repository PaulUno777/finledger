package com.pauluno.finledger.infrastructure.security;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;

/**
 * ADR-018: parent AGGREGATOR {@code ledger:admin} may access account routes of a
 * direct SUB_MERCHANT child.
 */
@Component
public class TenantHierarchyAccessChecker {

    private static final Pattern ACCOUNT_PATH = Pattern.compile(
            "^/api/v1/tenants/([0-9a-fA-F-]{36})/accounts(?:/[0-9a-fA-F-]{36}(?:/balance)?)?$");

    private final TenantRepository tenantRepository;

    public TenantHierarchyAccessChecker(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public boolean allowsParentAdminOnAccountRoute(
            String path,
            String claimTenantId,
            Authentication authentication
    ) {
        if (path == null || claimTenantId == null || claimTenantId.isBlank() || authentication == null) {
            return false;
        }
        Matcher matcher = ACCOUNT_PATH.matcher(path);
        if (!matcher.matches()) {
            return false;
        }
        if (!hasLedgerAdmin(authentication)) {
            return false;
        }
        UUID pathTenantId;
        UUID parentClaimId;
        try {
            pathTenantId = UUID.fromString(matcher.group(1));
            parentClaimId = UUID.fromString(claimTenantId);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        Tenant child = tenantRepository.findById(pathTenantId).orElse(null);
        Tenant parent = tenantRepository.findById(parentClaimId).orElse(null);
        if (child == null || parent == null) {
            return false;
        }
        return parent.type() == TenantType.AGGREGATOR
                && child.type() == TenantType.SUB_MERCHANT
                && parentClaimId.equals(child.parentTenantId());
    }

    private static boolean hasLedgerAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (LedgerAuthorities.SCOPE_LEDGER_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
