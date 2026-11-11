package com.example.taskapi.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** CORS allow-list taken from CORS_ALLOWED_ORIGINS. No origins configured means no cross-origin access. */
@Configuration(proxyBeanMethods = false)
class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().origins());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "If-Match", "If-None-Match", "X-Request-Id", "Last-Event-ID"));
        cors.setExposedHeaders(List.of("ETag", "Location", "X-Request-Id", "Retry-After"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
