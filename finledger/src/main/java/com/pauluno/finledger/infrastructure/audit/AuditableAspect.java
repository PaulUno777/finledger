package com.pauluno.finledger.infrastructure.audit;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pauluno.finledger.application.audit.AuditRecord;
import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.port.out.AuditLogWriter;
import com.pauluno.finledger.application.tenant.TenantContext;
import com.pauluno.finledger.domain.audit.AuditHashChain;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditableAspect {

    private final AuditLogWriter auditLogWriter;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    public AuditableAspect(AuditLogWriter auditLogWriter, Tracer tracer) {
        this.auditLogWriter = auditLogWriter;
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();
        appendAudit(joinPoint, auditable, result);
        return result;
    }

    private void appendAudit(ProceedingJoinPoint joinPoint, Auditable auditable, Object result) {
        UUID tenantId = resolveTenantId(joinPoint.getArgs(), result);
        if (tenantId == null) {
            throw new IllegalStateException(
                    "Cannot audit " + auditable.action() + ": tenantId not found on args/result");
        }

        UUID resourceId = resolveResourceId(result, joinPoint.getArgs());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", auditable.action());
        payload.put("resourceType", auditable.resourceType());
        payload.put("method", ((MethodSignature) joinPoint.getSignature()).toShortString());
        payload.put("tenantId", tenantId.toString());
        if (resourceId != null) {
            payload.put("resourceId", resourceId.toString());
        }
        Object firstArg = joinPoint.getArgs().length > 0 ? joinPoint.getArgs()[0] : null;
        if (firstArg != null) {
            payload.put("commandType", firstArg.getClass().getSimpleName());
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }

        String traceId = resolveTraceId();
        String spanId = resolveSpanId();

        auditLogWriter.append(new AuditRecord(
                tenantId,
                AuditHashChain.truncateToMicros(Instant.now()),
                resolveActor(),
                auditable.action(),
                auditable.resourceType(),
                resourceId,
                payloadJson,
                traceId,
                spanId
        ));
    }

    private String resolveTraceId() {
        Span span = tracer.currentSpan();
        if (span != null && span.context() != null && span.context().traceId() != null
                && !span.context().traceId().isBlank()) {
            return span.context().traceId();
        }
        return TraceContext.get().map(TraceContext.Parsed::traceId).orElse(null);
    }

    private String resolveSpanId() {
        Span span = tracer.currentSpan();
        if (span != null && span.context() != null && span.context().spanId() != null
                && !span.context().spanId().isBlank()) {
            return span.context().spanId();
        }
        return TraceContext.get().map(TraceContext.Parsed::spanId).orElse(null);
    }

    private static String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private static UUID resolveTenantId(Object[] args, Object result) {
        return TenantContext.get()
                .or(() -> invokeUuid(result, "tenantId"))
                .or(() -> {
                    for (Object arg : args) {
                        var fromArg = invokeUuid(arg, "tenantId");
                        if (fromArg.isPresent()) {
                            return fromArg;
                        }
                    }
                    return java.util.Optional.empty();
                })
                .orElse(null);
    }

    private static UUID resolveResourceId(Object result, Object[] args) {
        return invokeUuid(result, "journalEntryId")
                .or(() -> invokeUuid(result, "instructionId"))
                .or(() -> invokeUuid(result, "accountId"))
                .or(() -> invokeUuid(result, "tenantId"))
                .or(() -> {
                    for (Object arg : args) {
                        var id = invokeUuid(arg, "originalJournalEntryId");
                        if (id.isPresent()) {
                            return id;
                        }
                    }
                    return java.util.Optional.empty();
                })
                .orElse(null);
    }

    private static java.util.Optional<UUID> invokeUuid(Object target, String methodName) {
        if (target == null) {
            return java.util.Optional.empty();
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof UUID uuid) {
                return java.util.Optional.of(uuid);
            }
            return java.util.Optional.empty();
        } catch (ReflectiveOperationException e) {
            return java.util.Optional.empty();
        }
    }
}
