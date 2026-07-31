package com.pauluno.finledger.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * No AuthN — sandbox principal only (FL-151). Forbidden in production by {@link SecurityModeGuard}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "finledger.security", name = "mode", havingValue = "disabled")
public class DisabledSecurityConfig {

    @Bean
    SecurityFilterChain disabledSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(new SandboxPrincipalFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new TenantClaimAuthorizationFilter(
                                TenantClaimAuthorizationFilter.TenantBindingMode.SANDBOX_ONLY),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
