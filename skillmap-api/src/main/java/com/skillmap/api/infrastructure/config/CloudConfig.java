package com.skillmap.api.infrastructure.config;

import com.skillmap.api.infrastructure.ai.GeminiJobSkillExtractor;
import com.skillmap.api.infrastructure.security.AiDailyLimiter;
import com.skillmap.api.presentation.controller.AiController;
import com.skillmap.api.presentation.controller.HealthController;
import com.skillmap.api.presentation.exception.GlobalExceptionHandler;
import com.skillmap.api.presentation.filter.RequestBodySizeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Clock;
import java.util.List;

/**
 * Perfil "cloud": solo el servicio de IA (POST /api/ai/extract-skills) y GET /api/health, para
 * desplegar en Render. Nada de usuarios, base de datos, ofertas, sincronización ni escritorio:
 * esas clases solo las escanea el perfil de escritorio (ver SkillmapApiApplication) y la base
 * se desactiva en application-cloud.properties. La key sale solo de la variable GEMINI_API_KEY.
 */
@Configuration(proxyBeanMethods = false)
@Profile(CloudConfig.PROFILE)
@Import({GeminiJobSkillExtractor.class, AiController.class, HealthController.class, GlobalExceptionHandler.class})
public class CloudConfig {

    public static final String PROFILE = "cloud";
    /** 8.000 caracteres caben de sobra aunque vengan escapados en el JSON; más que esto ni se lee. */
    static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Logger log = LoggerFactory.getLogger(CloudConfig.class);

    @Bean
    public GeminiApiKeyProvider geminiApiKeyProvider() {
        GeminiApiKeyProvider provider = GeminiApiKeyProvider.fromEnvironmentVariable();
        if (provider.getApiKey().isEmpty()) {
            log.warn("GEMINI_API_KEY no está definida: /api/ai/extract-skills responderá 503");
        }
        return provider;
    }

    @Bean
    public AiDailyLimiter aiDailyLimiter(@Value("${skillmap.ai.daily-limit-per-ip}") int perIpLimit,
                                         @Value("${skillmap.ai.daily-limit-global}") int globalLimit) {
        return new AiDailyLimiter(perIpLimit, globalLimit, Clock.systemUTC());
    }

    /**
     * Cualquier origen, solo para la IA y el health: la app de escritorio llama desde localhost con
     * un puerto que cambia. Sin cookies ni credenciales, así que abrirlo no expone datos de nadie.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> cloudCorsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE));
        config.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/ai/**", config);
        source.registerCorsConfiguration("/api/health", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestBodySizeFilter> aiBodySizeFilter() {
        FilterRegistrationBean<RequestBodySizeFilter> registration =
                new FilterRegistrationBean<>(new RequestBodySizeFilter("/api/ai", MAX_BODY_BYTES));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
