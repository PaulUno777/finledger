package com.pauluno.finledger.application.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped current tenant for Postgres RLS ({@code app.current_tenant_id}).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(Objects.requireNonNull(tenantId, "tenantId"));
        BYPASS.set(Boolean.FALSE);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void enableBypass() {
        BYPASS.set(Boolean.TRUE);
        CURRENT.remove();
    }

    public static boolean isBypass() {
        return Boolean.TRUE.equals(BYPASS.get());
    }

    public static void clear() {
        CURRENT.remove();
        BYPASS.remove();
    }
}
