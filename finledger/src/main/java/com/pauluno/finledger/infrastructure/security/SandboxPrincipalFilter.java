package com.pauluno.finledger.infrastructure.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates every request as the sandbox principal ({@code disabled} mode).
 */
public final class SandboxPrincipalFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken(
                "sandbox",
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
}
