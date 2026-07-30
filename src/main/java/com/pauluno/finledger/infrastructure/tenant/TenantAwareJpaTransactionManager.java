package com.pauluno.finledger.infrastructure.tenant;

import java.util.UUID;

import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.pauluno.finledger.application.tenant.TenantContext;

import jakarta.persistence.EntityManagerFactory;

/**
 * Applies Postgres RLS session GUCs with {@code SET LOCAL} immediately after the JPA
 * transaction begins, so they stick for the whole TX regardless of AOP advisor order.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    public TenantAwareJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(
                obtainEntityManagerFactory());
        if (holder == null || holder.getEntityManager() == null) {
            return;
        }
        Session session = holder.getEntityManager().unwrap(Session.class);
        session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                if (TenantContext.isBypass()) {
                    statement.execute("SELECT set_config('app.rls_bypass', 'on', true)");
                    statement.execute("SELECT set_config('app.current_tenant_id', '', true)");
                } else {
                    statement.execute("SELECT set_config('app.rls_bypass', 'off', true)");
                    String tenantId = TenantContext.get().map(UUID::toString).orElse("");
                    statement.execute(
                            "SELECT set_config('app.current_tenant_id', '"
                                    + tenantId.replace("'", "''")
                                    + "', true)");
                }
            }
        });
    }
}
