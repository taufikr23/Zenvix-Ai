package com.documind.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccountJwtConverter converter,
                                                    SecurityErrorHandler errors) throws Exception {
        return http
                // API hanya menerima Bearer header; tidak memakai cookie autentikasi.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/documents", "/api/documents/*/process", "/api/documents/*/index", "/api/documents/*/search",
                                "/api/conversations", "/api/conversations/*/messages").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/documents", "/api/documents/*",
                                "/api/documents/*/download", "/api/documents/*/chunks").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/conversations", "/api/conversations/*",
                                "/api/conversations/*/messages", "/api/conversations/*/attachments/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/images/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/documents/*", "/api/conversations/*").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .build();
    }
}
