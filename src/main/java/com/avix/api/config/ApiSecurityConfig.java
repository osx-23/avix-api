package com.avix.api.config;

import com.avix.api.security.AvixAuth;
import com.avix.api.security.AvixBearerFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ApiSecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AvixAuth auth, ObjectMapper mapper) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, problem) ->
                                AvixBearerFilter.error(res, mapper, 401, "Se requiere una sesión válida."))
                        .accessDeniedHandler((req, res, problem) ->
                                AvixBearerFilter.error(res, mapper, 403, "No tienes permiso para esta operación.")))
                .authorizeHttpRequests(access -> access
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/web", "/api/v1/auth/movil",
                                "/api/v1/setup/supervisor").permitAll()
                        .requestMatchers("/api/v1/admin/**")
                                .hasAllAuthorities("ROLE_SUPERVISOR", "CLIENT_WEB")
                        .requestMatchers(HttpMethod.POST, "/api/v1/registros").hasAuthority("CLIENT_MOVIL")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/registros/**").hasAuthority("CLIENT_WEB")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new AvixBearerFilter(auth, mapper), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
