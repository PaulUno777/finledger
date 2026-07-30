package com.pauluno.finledger.infrastructure.security;

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
 * OIDC resource server: JWT bearer auth, fine scopes, tenant claim binding (plan §11).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String SCOPE_LEDGER_READ = "SCOPE_ledger:read";
    public static final String SCOPE_LEDGER_WRITE = "SCOPE_ledger:write";
    public static final String SCOPE_LEDGER_ADMIN = "SCOPE_ledger:admin";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/rails/webhooks/settlement")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants").hasAuthority(SCOPE_LEDGER_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyAuthority(SCOPE_LEDGER_READ, SCOPE_LEDGER_WRITE, SCOPE_LEDGER_ADMIN)
                        .requestMatchers("/api/**")
                        .hasAnyAuthority(SCOPE_LEDGER_WRITE, SCOPE_LEDGER_ADMIN)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterAfter(new TenantClaimAuthorizationFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
