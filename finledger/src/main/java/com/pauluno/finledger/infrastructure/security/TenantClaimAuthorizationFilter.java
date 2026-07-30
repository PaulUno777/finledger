package com.pauluno.finledger.infrastructure.security;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ensures JWT claim {@code tenant_id} matches the tenant UUID in the request path
 * for tenant-scoped APIs (plan §11).
 */
public class TenantClaimAuthorizationFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_CLAIM = "tenant_id";

    private static final Pattern TENANT_SCOPED_PATH =
            Pattern.compile("^/api/v1/tenants/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(/|$)");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isCreateTenant(request, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Matcher matcher = TENANT_SCOPED_PATH.matcher(path);
        if (!matcher.find()) {
            filterChain.doFilter(request, response);
            return;
        }

        String pathTenantId = matcher.group(1);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String claimTenantId = jwt.getClaimAsString(TENANT_ID_CLAIM);
        if (claimTenantId == null || claimTenantId.isBlank() || !claimTenantId.equals(pathTenantId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"TENANT_CLAIM_MISMATCH\",\"message\":\"JWT tenant_id claim does not match path tenant\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isCreateTenant(HttpServletRequest request, String path) {
        return HttpMethod.POST.matches(request.getMethod())
                && ("/api/v1/tenants".equals(path) || "/api/v1/tenants/".equals(path));
    }
}
