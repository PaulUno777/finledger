package com.pauluno.finledger.infrastructure.security.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.pauluno.finledger.infrastructure.security.LedgerAuthorities;
import com.pauluno.finledger.infrastructure.security.PublicSecurityPaths;
import com.pauluno.finledger.infrastructure.security.TenantClaimAuthorizationFilter;
import com.pauluno.finledger.infrastructure.security.TenantHierarchyAccessChecker;

/**
 * JWT resource server backed by the in-box issuer (sandbox ephemeral or normal persistent).
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "internal")
public class InternalIssuerSecurityConfig {

    @Bean
    SecurityFilterChain internalIssuerSecurityFilterChain(
            HttpSecurity http,
            TenantHierarchyAccessChecker hierarchyAccessChecker,
            com.pauluno.finledger.infrastructure.security.FinledgerJwtAuthenticationConverter jwtAuthenticationConverter,
            com.pauluno.finledger.infrastructure.security.FinledgerSecurityProperties securityProperties
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PublicSecurityPaths.MATCHERS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/rails/webhooks/settlement")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                        .hasAnyAuthority(
                                LedgerAuthorities.SCOPE_LEDGER_ADMIN,
                                LedgerAuthorities.SCOPE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/platform/provision")
                        .hasAuthority(LedgerAuthorities.SCOPE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyAuthority(
                                LedgerAuthorities.SCOPE_LEDGER_READ,
                                LedgerAuthorities.SCOPE_LEDGER_WRITE,
                                LedgerAuthorities.SCOPE_LEDGER_ADMIN)
                        .requestMatchers("/api/**")
                        .hasAnyAuthority(
                                LedgerAuthorities.SCOPE_LEDGER_WRITE,
                                LedgerAuthorities.SCOPE_LEDGER_ADMIN)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                        .addFilterAfter(
                                new TenantClaimAuthorizationFilter(
                                        hierarchyAccessChecker,
                                        securityProperties.getClaim().getTenantId()),
                                BearerTokenAuthenticationFilter.class)
                .build();
    }
}
