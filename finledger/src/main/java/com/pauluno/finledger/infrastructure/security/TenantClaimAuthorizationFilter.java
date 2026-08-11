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
 * Binds JWT tenant claim to {@code /api/v1/tenants/{tenantId}/…} (ADR-016, ADR-018).
 */
public class TenantClaimAuthorizationFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_CLAIM = "tenant_id";

    private static final Pattern TENANT_SCOPED_PATH =
            Pattern.compile("^/api/v1/tenants/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(/|$)");

    private final TenantHierarchyAccessChecker hierarchyAccessChecker;
    private final String tenantIdClaim;

    public TenantClaimAuthorizationFilter() {
        this(null, TENANT_ID_CLAIM);
    }

    public TenantClaimAuthorizationFilter(TenantHierarchyAccessChecker hierarchyAccessChecker) {
        this(hierarchyAccessChecker, TENANT_ID_CLAIM);
    }

    public TenantClaimAuthorizationFilter(
            TenantHierarchyAccessChecker hierarchyAccessChecker,
            String tenantIdClaim
    ) {
        this.hierarchyAccessChecker = hierarchyAccessChecker;
        this.tenantIdClaim = tenantIdClaim == null || tenantIdClaim.isBlank()
                ? TENANT_ID_CLAIM
                : tenantIdClaim;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isCreateTenant(request, path) || isPlatformControlPlane(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Matcher matcher = TENANT_SCOPED_PATH.matcher(path);
        if (!matcher.find()) {
            filterChain.doFilter(request, response);
            return;
        }

        String pathTenantId = matcher.group(1);
        if (!jwtClaimMatches(pathTenantId, path)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"TENANT_CLAIM_MISMATCH\",\"message\":\"Caller tenant does not match path tenant\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean jwtClaimMatches(String pathTenantId, String path) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return true;
        }
        Jwt jwt = jwtAuth.getToken();
        String claimTenantId = jwt.getClaimAsString(tenantIdClaim);
        if (claimTenantId != null && !claimTenantId.isBlank() && claimTenantId.equals(pathTenantId)) {
            return true;
        }
        return hierarchyAccessChecker != null
                && hierarchyAccessChecker.allowsParentAdminOnAccountRoute(path, claimTenantId, authentication);
    }

    private static boolean isCreateTenant(HttpServletRequest request, String path) {
        return HttpMethod.POST.matches(request.getMethod())
                && ("/api/v1/tenants".equals(path) || "/api/v1/tenants/".equals(path));
    }

    /** Control-plane routes: scope alone authorizes; no tenant_id claim (FL-158). */
    private static boolean isPlatformControlPlane(String path) {
        return path != null && (path.equals("/api/v1/platform")
                || path.startsWith("/api/v1/platform/")
                || path.equals("/api/v1/platform-admins")
                || path.startsWith("/api/v1/platform-admins/"));
    }
}
