package com.Second_Brain.Second_Brain.common.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Spring renders errors by forwarding to /error. Without this,
                // that forward needs a token too, so every error on a public
                // endpoint became a 401. A direct request to /error still needs one.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/test/embedding").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(
                    "/actuator/health",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
