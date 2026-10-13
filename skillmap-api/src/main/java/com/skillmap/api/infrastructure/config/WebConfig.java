package com.skillmap.api.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS de la API. El frontend que sirve la propia app es del mismo origen y no lo
 * necesita; solo se permiten los orígenes de skillmap.cors.allowed-origins (por defecto
 * Live Server, para desarrollo). Abrirlo a cualquier origen dejaría que cualquier web
 * abierta en el navegador del usuario leyera sus datos y gastara su cuota de Gemini.
 *
 * Es un filtro (no la config de Spring MVC) para ir antes del de autenticación: así un
 * origen no permitido recibe 403 y las preflight no necesitan token.
 */
@Configuration
public class WebConfig {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${skillmap.cors.allowed-origins:}") String[] allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins)
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        config.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
