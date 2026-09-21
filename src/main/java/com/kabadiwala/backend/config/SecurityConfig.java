package com.kabadiwala.backend.config;

import com.kabadiwala.backend.auth.SupabaseJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // OpenAPI / Swagger endpoints
                        .requestMatchers(
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // Public taxonomy inspection
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        // Public static image uploads (for browser <img> rendering)
                        .requestMatchers(HttpMethod.GET, "/api/v1/storage/files/**").permitAll()
                        // Collector workflows
                        .requestMatchers("/api/v1/material-lots/**", "/api/v1/lots/**").hasAnyRole("COLLECTOR", "ADMIN")
                        // Recycler / Collector shared transaction endpoints
                        .requestMatchers("/api/v1/transactions/**").authenticated()
                        // Pricing inspection
                        .requestMatchers(HttpMethod.GET, "/api/v1/pricing/**").authenticated()
                        // Recyclers directory
                        .requestMatchers(HttpMethod.GET, "/api/v1/recyclers/**").authenticated()
                        // All other APIs require authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new SupabaseJwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder(
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256);
                    algorithms.add(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256);
                })
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
