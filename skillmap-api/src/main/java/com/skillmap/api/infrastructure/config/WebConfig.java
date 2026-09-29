package com.skillmap.api.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para que el frontend (servido desde otro origen, ej. Live
 * Server o un archivo local) pueda llamar a esta API sin que el navegador
 * lo bloquee. Abierto a cualquier origen porque estamos en desarrollo;
 * cuando haya dominio de producción, se restringe a esa URL puntual.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE");
    }
}
