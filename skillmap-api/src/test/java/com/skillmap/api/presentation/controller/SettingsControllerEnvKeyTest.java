package com.skillmap.api.presentation.controller;

import com.skillmap.api.support.TestAuth;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con la variable de entorno GEMINI_API_KEY definida: tiene prioridad sobre la key guardada. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-settings-env-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-settings-env-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY=" + SettingsControllerEnvKeyTest.ENV_KEY
})
@AutoConfigureMockMvc
class SettingsControllerEnvKeyTest {

    static final String ENV_KEY = "env-key-NOT-REAL-0123456789abcdef";
    private static final String FILE_KEY = "file-key-NOT-REAL-0123456789abcdef";

    @Autowired
    MockMvc mvc;

    @Value("${skillmap.config-dir}")
    Path configDir;

    @BeforeEach
    void reset() throws Exception {
        Files.createDirectories(configDir);
        Files.deleteIfExists(configDir.resolve("config.properties"));

        token = TestAuth.register(mvc);
    }

    private String token;

    /** Todas las peticiones con la sesión de un usuario recién registrado. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(TestAuth.bearer(token)));
    }

    @Test
    void reportsEnvironmentAsSource() throws Exception {
        perform(get("/api/settings"))
                .andExpect(jsonPath("$.gemini.configured").value(true))
                .andExpect(jsonPath("$.gemini.source").value("env"))
                .andExpect(content().string(not(containsString(ENV_KEY))));
    }

    @Test
    void savesKeyAnywayButWarnsThatVariableWins() throws Exception {
        perform(put("/api/settings/gemini-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + FILE_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.source").value("env"))
                .andExpect(jsonPath("$.warning").value(containsString("GEMINI_API_KEY")))
                .andExpect(content().string(not(containsString(FILE_KEY))))
                .andExpect(content().string(not(containsString(ENV_KEY))));

        assertThat(Files.readString(configDir.resolve("config.properties"), StandardCharsets.UTF_8))
                .contains("gemini.api.key=" + FILE_KEY);
    }
}
