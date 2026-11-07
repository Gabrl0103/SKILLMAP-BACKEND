package com.skillmap.api;

import com.skillmap.api.infrastructure.config.CloudConfig;
import com.skillmap.api.infrastructure.desktop.PortSelector;
import com.skillmap.api.infrastructure.desktop.SingleInstanceGuard;
import com.skillmap.api.infrastructure.desktop.StdinLifeline;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Como @SpringBootApplication, pero el escaneo de componentes solo se hace fuera del perfil
 * cloud: ahí únicamente se carga lo que importa {@link CloudConfig}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CloudConfig.class)
public class SkillmapApiApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SkillmapApiApplication.class);
        // Antes de crear el contexto: una sola instancia y un puerto libre (no en el perfil cloud).
        // StdinLifeline solo actúa si la lanza la app de escritorio (skillmap.desktop.exit-on-stdin-close).
        app.addListeners(new StdinLifeline(), new SingleInstanceGuard(), new PortSelector());
        app.run(args);
    }

    /** App de escritorio completa: todos los componentes del paquete, con los filtros de @SpringBootApplication. */
    @Configuration(proxyBeanMethods = false)
    @Profile("!" + CloudConfig.PROFILE)
    @ComponentScan(basePackageClasses = SkillmapApiApplication.class, excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)})
    static class DesktopComponents {
    }
}
