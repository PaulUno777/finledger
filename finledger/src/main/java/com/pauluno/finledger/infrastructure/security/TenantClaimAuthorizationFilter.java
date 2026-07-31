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

import com.pauluno.finledger.security.policy.SandboxIds;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ensures the caller is bound to the tenant UUID in the request path (plan §11 / FL-151).
 * <ul>
 *   <li>{@code enforced}: JWT claim {@code tenant_id}</li>
 *   <li>{@code static-token}: header {@code X-FinLedger-Tenant-Id}</li>
 *   <li>{@code disabled}: path tenant must equal the well-known sandbox tenant</li>
 * </ul>
 */
public class TenantClaimAuthorizationFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_CLAIM = "tenant_id";

    public enum TenantBindingMode {
        JWT_CLAIM,
        HEADER,
        SANDBOX_ONLY
    }

    private static final Pattern TENANT_SCOPED_PATH =
            Pattern.compile("^/api/v1/tenants/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(/|$)");

    private final TenantBindingMode bindingMode;

    public TenantClaimAuthorizationFilter() {
        this(TenantBindingMode.JWT_CLAIM);
    }

    public TenantClaimAuthorizationFilter(TenantBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

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
        if (!tenantMatches(request, pathTenantId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"TENANT_CLAIM_MISMATCH\",\"message\":\"Caller tenant does not match path tenant\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean tenantMatches(HttpServletRequest request, String pathTenantId) {
        return switch (bindingMode) {
            case JWT_CLAIM -> jwtClaimMatches(pathTenantId);
            case HEADER -> headerMatches(request, pathTenantId);
            case SANDBOX_ONLY -> SandboxIds.TENANT_ID.toString().equalsIgnoreCase(pathTenantId);
        };
    }

    private static boolean jwtClaimMatches(String pathTenantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return true;
        }
        Jwt jwt = jwtAuth.getToken();
        String claimTenantId = jwt.getClaimAsString(TENANT_ID_CLAIM);
        return claimTenantId != null && !claimTenantId.isBlank() && claimTenantId.equals(pathTenantId);
    }

    private static boolean headerMatches(HttpServletRequest request, String pathTenantId) {
        String header = request.getHeader(LedgerAuthorities.TENANT_HEADER);
        return header != null && !header.isBlank() && header.trim().equalsIgnoreCase(pathTenantId);
    }

    private static boolean isCreateTenant(HttpServletRequest request, String path) {
        return HttpMethod.POST.matches(request.getMethod())
                && ("/api/v1/tenants".equals(path) || "/api/v1/tenants/".equals(path));
    }
}
