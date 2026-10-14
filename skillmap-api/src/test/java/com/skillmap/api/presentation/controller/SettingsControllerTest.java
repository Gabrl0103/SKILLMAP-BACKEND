package com.skillmap.api.presentation.controller;

import com.skillmap.api.infrastructure.persistence.AppSettingJpaRepository;
import com.skillmap.api.support.TestAuth;
import org.springframework.test.web.servlet.ResultActions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de Ajustes con el contexto completo. config.properties va a una carpeta temporal (nunca a
 * %APPDATA%\SkillMap), la base es en memoria y GEMINI_API_KEY se anula aunque exista en el equipo.
 * El lector del cuerpo de Spring registra en DEBUG lo que lee: así se comprueba que ni con eso
 * aparece la key en los logs.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-settings-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-settings-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY=",
        "logging.level.org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor=DEBUG"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SettingsControllerTest {

    /** Sin el formato AIza...: ningún filtro de logs la tapa, así que no debe llegar a ellos en absoluto. */
    private static final String KEY = "test-key-NOT-REAL-0123456789abcdef";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppSettingJpaRepository settingsTable;

    @Value("${skillmap.config-dir}")
    Path configDir;

    @BeforeEach
    void reset() throws Exception {
        settingsTable.deleteAll();
        Files.createDirectories(configDir);
        Files.deleteIfExists(configFile());

        token = TestAuth.register(mvc);
    }

    private String token;

    /** Todas las peticiones con la sesión de un usuario recién registrado. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(TestAuth.bearer(token)));
    }

    // ---------- GET / PATCH ----------

    @Test
    void returnsDefaults() throws Exception {
        perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("light"))
                .andExpect(jsonPath("$.showIntro").value(true))
                .andExpect(jsonPath("$.reduceMotion").value(false))
                .andExpect(jsonPath("$.gemini.configured").value(false))
                .andExpect(jsonPath("$.gemini.source").value("none"));
    }

    @Test
    void patchUpdatesOnlyGivenFieldsAndIgnoresUnknownOnes() throws Exception {
        perform(json(patch("/api/settings"), "{\"theme\":\"dark\",\"desconocido\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.showIntro").value(true))
                .andExpect(jsonPath("$.reduceMotion").value(false))
                .andExpect(jsonPath("$.desconocido").doesNotExist());

        perform(json(patch("/api/settings"), "{\"reduceMotion\":true,\"showIntro\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"));

        perform(get("/api/settings"))
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.showIntro").value(false))
                .andExpect(jsonPath("$.reduceMotion").value(true));
    }

    @Test
    void patchRejectsInvalidThemeWithClearMessage() throws Exception {
        perform(json(patch("/api/settings"), "{\"theme\":\"purple\",\"reduceMotion\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El tema no es válido. Valores permitidos: light, dark."))
                .andExpect(content().string(not(containsString("purple"))));

        // Nada se aplicó a medias.
        perform(get("/api/settings"))
                .andExpect(jsonPath("$.theme").value("light"))
                .andExpect(jsonPath("$.reduceMotion").value(false));
    }

    @Test
    void patchRejectsWrongTypeNamingTheField() throws Exception {
        perform(json(patch("/api/settings"), "{\"showIntro\":{\"a\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El campo 'showIntro' no tiene un valor válido."));
    }

    @Test
    void patchWithoutBodyIsBadRequest() throws Exception {
        perform(patch("/api/settings").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ---------- PUT gemini-key ----------

    @Test
    void savesKeyToFileAndAnswersNoContent() throws Exception {
        Files.writeString(configFile(), "# mío\notra.propiedad=1\n", StandardCharsets.UTF_8);

        MvcResult result = perform(json(put("/api/settings/gemini-key"), keyBody(KEY)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(Files.readString(configFile(), StandardCharsets.UTF_8))
                .isEqualTo("# mío\notra.propiedad=1\ngemini.api.key=" + KEY + "\n");
        perform(get("/api/settings"))
                .andExpect(jsonPath("$.gemini.configured").value(true))
                .andExpect(jsonPath("$.gemini.source").value("file"))
                .andExpect(content().string(not(containsString(KEY))));
    }

    @Test
    void rejectsInvalidKeyFormatWithoutSavingNorEchoing() throws Exception {
        String withSpaces = "test-key NOT-REAL-0123456789abcdef";

        perform(json(put("/api/settings/gemini-key"), keyBody(withSpaces)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La key de Gemini no puede contener espacios."))
                .andExpect(content().string(not(containsString("NOT-REAL"))));
        perform(json(put("/api/settings/gemini-key"), keyBody("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La key de Gemini no puede estar vacía."));
        perform(json(put("/api/settings/gemini-key"), "{}"))
                .andExpect(status().isBadRequest());

        assertThat(configFile()).doesNotExist();
    }

    @Test
    void malformedJsonIsRejectedWithoutEchoingTheBody() throws Exception {
        perform(json(put("/api/settings/gemini-key"), "{\"key\": " + KEY + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es un JSON válido."))
                .andExpect(content().string(not(containsString(KEY))));
    }

    @Test
    void rejectsTooLargeBody() throws Exception {
        String huge = "{\"key\":\"" + KEY + "\",\"relleno\":\"" + "x".repeat(5000) + "\"}";

        perform(json(put("/api/settings/gemini-key"), huge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(content().string(not(containsString(KEY))));
        perform(json(patch("/api/settings"), "{\"theme\":\"dark\",\"x\":\"" + "x".repeat(5000) + "\"}"))
                .andExpect(status().isPayloadTooLarge());

        assertThat(configFile()).doesNotExist();
    }

    @Test
    void keyNeverReachesTheLogs(CapturedOutput output) throws Exception {
        perform(json(put("/api/settings/gemini-key"), keyBody(KEY))).andExpect(status().isNoContent());
        perform(json(put("/api/settings/gemini-key"), keyBody(KEY + " extra"))).andExpect(status().isBadRequest());
        perform(json(put("/api/settings/gemini-key"), "{\"key\": " + KEY + "}")).andExpect(status().isBadRequest());
        perform(json(put("/api/settings/gemini-key"), keyBody(KEY + "x".repeat(5000))))
                .andExpect(status().isPayloadTooLarge());
        perform(get("/api/settings")).andExpect(status().isOk());

        // El cuerpo leído sí se registró (DEBUG), pero con la key tapada.
        assertThat(output.getAll())
                .contains("GeminiKeyRequest[key=***]")
                .doesNotContain(KEY)
                .doesNotContain("test-key-NOT-REAL");
    }

    private Path configFile() {
        return configDir.resolve("config.properties");
    }

    private static String keyBody(String key) {
        return "{\"key\":\"" + key + "\"}";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
