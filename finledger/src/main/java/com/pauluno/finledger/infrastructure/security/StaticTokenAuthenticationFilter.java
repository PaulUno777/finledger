package com.pauluno.finledger.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Accepts a single shared Bearer secret for {@code static-token} mode (FL-151).
 */
public final class StaticTokenAuthenticationFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public StaticTokenAuthenticationFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            unauthorized(response, "Missing Bearer token");
            return;
        }
        String presented = header.substring(7).trim();
        if (expectedToken == null || expectedToken.isBlank() || !constantTimeEquals(expectedToken, presented)) {
            unauthorized(response, "Invalid static token");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "static-token",
                null,
                List.of(
                        new SimpleGrantedAuthority(LedgerAuthorities.SCOPE_LEDGER_READ),
                        new SimpleGrantedAuthority(LedgerAuthorities.SCOPE_LEDGER_WRITE),
                        new SimpleGrantedAuthority(LedgerAuthorities.SCOPE_LEDGER_ADMIN)
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static boolean isPublic(String path) {
        return path.startsWith("/actuator/health")
                || "/actuator/prometheus".equals(path)
                || path.contains("/rails/webhooks/settlement");
    }

    private static void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
