package org.example.orderservice.security.config;

import lombok.RequiredArgsConstructor;
import org.example.orderservice.security.gateway.GatewayAuthHeaderFilter;
import org.example.orderservice.security.jwt.JwtAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Placing/viewing orders requires an authenticated caller, but the JWT itself is now verified
 * once at api-gateway - order-service just trusts the identity it forwards (see
 * GatewayAuthHeaderFilter). This assumes order-service is unreachable except through the gateway.
 */
@RequiredArgsConstructor
@EnableWebSecurity
@Configuration
public class SecurityConfig {
    private final JwtAuthEntryPoint authEntryPoint;

    @Bean
    public GatewayAuthHeaderFilter gatewayAuthHeaderFilter() {
        return new GatewayAuthHeaderFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/orders/**").authenticated()
                        .anyRequest().permitAll());
        http.addFilterBefore(gatewayAuthHeaderFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
