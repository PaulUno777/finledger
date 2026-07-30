package com.pauluno.finledger.infrastructure.tenant;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pauluno.finledger.application.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sets {@link TenantContext} from {@code /api/v1/tenants/{tenantId}/...} path segments.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Pattern TENANT_PATH = Pattern.compile("^/api/v1/tenants/([^/]+)/");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Matcher matcher = TENANT_PATH.matcher(request.getRequestURI());
            if (matcher.find()) {
                try {
                    TenantContext.set(UUID.fromString(matcher.group(1)));
                } catch (IllegalArgumentException ignored) {
                    // Let controllers return 400 for malformed UUIDs.
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
