package com.skillmap.api;

import com.skillmap.api.infrastructure.desktop.PortSelector;
import com.skillmap.api.infrastructure.desktop.SingleInstanceGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillmapApiApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SkillmapApiApplication.class);
        // Antes de crear el contexto: una sola instancia y un puerto libre.
        app.addListeners(new SingleInstanceGuard(), new PortSelector());
        app.run(args);
    }
}
