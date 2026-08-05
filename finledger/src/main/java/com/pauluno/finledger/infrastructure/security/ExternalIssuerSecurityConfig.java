package com.pauluno.finledger.infrastructure.security;

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

/**
 * JWT resource server for external IdP issuer (default for profile {@code normal}).
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "external", matchIfMissing = true)
public class ExternalIssuerSecurityConfig {

    @Bean
    SecurityFilterChain externalIssuerSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PublicSecurityPaths.MATCHERS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/rails/webhooks/settlement")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                        .hasAnyAuthority(
                                LedgerAuthorities.SCOPE_LEDGER_ADMIN,
                                LedgerAuthorities.SCOPE_PLATFORM_ADMIN)
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
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterAfter(new TenantClaimAuthorizationFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
