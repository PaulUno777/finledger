package com.pauluno.finledger.infrastructure.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * TX advisor is outermost so {@link AuditableAspect} runs inside the same DB transaction.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class AuditAopConfig {
}
