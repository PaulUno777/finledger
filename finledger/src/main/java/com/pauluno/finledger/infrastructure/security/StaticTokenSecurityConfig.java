package com.pauluno.finledger.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.pauluno.finledger.application.port.out.SecretsProvider;

/**
 * Shared bearer token mode for CI / early integration (FL-151).
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "finledger.security", name = "mode", havingValue = "static-token")
public class StaticTokenSecurityConfig {

    @Bean
    SecurityFilterChain staticTokenSecurityFilterChain(
            HttpSecurity http,
            SecretsProvider secretsProvider,
            StaticTokenHolder staticTokenHolder
    ) throws Exception {
        String token = staticTokenHolder.resolve(secretsProvider);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/rails/webhooks/settlement")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                        .hasAuthority(LedgerAuthorities.SCOPE_LEDGER_ADMIN)
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
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(
                        new StaticTokenAuthenticationFilter(token),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new TenantClaimAuthorizationFilter(TenantClaimAuthorizationFilter.TenantBindingMode.HEADER),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
