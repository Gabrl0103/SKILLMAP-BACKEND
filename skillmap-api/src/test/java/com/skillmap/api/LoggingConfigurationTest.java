package com.skillmap.api;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * logback-spring.xml: el archivo de log va a skillmap.logs-dir, rota en 3 archivos de ~5 MB
 * y nunca contiene la key de Gemini.
 */
class LoggingConfigurationTest {

    /** Con el formato de las keys de Google: AIza + 35 caracteres. */
    private static final String FAKE_KEY = "AIza" + "FakeKey0123456789_-abcdefghijklmnop";

    @TempDir
    Path logsDir;

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
    private final Logger log = LoggerFactory.getLogger(LoggingConfigurationTest.class);

    @BeforeEach
    void initLogging() {
        MockEnvironment environment = new MockEnvironment().withProperty("skillmap.logs-dir", logsDir.toString());
        // Otro test pudo dejar logback configurado por Spring: sin esto initialize no haría nada.
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    }

    @AfterEach
    void resetLogging() throws Exception {
        // Cierra el archivo antes de que JUnit borre la carpeta temporal (en Windows no se podría).
        loggingSystem.cleanUp();
        LoggerContext context = loggerContext();
        context.reset();
        new ContextInitializer(context).autoConfig();
    }

    @Test
    void writesToLogsDir() throws Exception {
        log.info("Mensaje de prueba");

        assertThat(readLog()).contains("Mensaje de prueba");
    }

    @Test
    void neverWritesTheGeminiKey() throws Exception {
        log.info("Key suelta: {}", FAKE_KEY);
        log.info("Ajuste gemini.api.key={} y GEMINI_API_KEY = otraKeySecreta", FAKE_KEY);
        log.warn("Cabecera x-goog-api-key: cabeceraSecreta");
        log.error("Fallo al llamar a Gemini", new IllegalStateException("respuesta con " + FAKE_KEY));

        String content = readLog();
        assertThat(content)
                .doesNotContain(FAKE_KEY)
                .doesNotContain("otraKeySecreta")
                .doesNotContain("cabeceraSecreta")
                .contains("Key suelta: ***")
                .contains("gemini.api.key=***")
                .contains("x-goog-api-key=***")
                .contains("respuesta con ***")
                // La traza sigue en el log, solo sin la key.
                .contains("at com.skillmap.api.LoggingConfigurationTest");
        // Lo tapado no se come el salto de línea: cada mensaje sigue en su línea.
        assertThat(content.lines())
                .anyMatch(line -> line.endsWith("gemini.api.key=*** y GEMINI_API_KEY=***"))
                .anyMatch(line -> line.endsWith("Cabecera x-goog-api-key=***"));
    }

    @Test
    void rotatesInThreeFilesOfFiveMegabytes() throws Exception {
        RollingFileAppender<?> appender = (RollingFileAppender<?>) loggerContext()
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("FILE");
        FixedWindowRollingPolicy rolling = (FixedWindowRollingPolicy) appender.getRollingPolicy();
        SizeBasedTriggeringPolicy<?> trigger = (SizeBasedTriggeringPolicy<?>) appender.getTriggeringPolicy();
        assertThat(trigger.getMaxFileSize().getSize()).isEqualTo(FileSize.valueOf("5MB").getSize());
        assertThat(rolling.getMinIndex()).isEqualTo(1);
        assertThat(rolling.getMaxIndex()).isEqualTo(2);

        for (int i = 0; i < 4; i++) {
            log.info("Antes de la rotación {}", i);
            appender.rollover();
        }

        try (var files = Files.list(logsDir)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .containsExactlyInAnyOrder("skillmap.log", "skillmap.1.log", "skillmap.2.log");
        }
    }

    private String readLog() throws Exception {
        return Files.readString(logsDir.resolve("skillmap.log"), StandardCharsets.UTF_8);
    }

    private static LoggerContext loggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }
}
