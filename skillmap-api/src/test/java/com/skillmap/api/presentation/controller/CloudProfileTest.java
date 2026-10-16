package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.AuthService;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.infrastructure.config.DataSeeder;
import com.skillmap.api.infrastructure.desktop.DesktopLauncher;
import com.skillmap.api.infrastructure.scheduling.JobSyncScheduler;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil cloud de punta a punta con MockMvc. Gemini nunca se llama: el extractor es un fake.
 * El cupo se comparte entre los tests (mismo contexto): cada test usa su propia IP. Los
 * lectores de cuerpos de Spring en DEBUG para comprobar que ni así aparece el texto de la oferta.
 */
@SpringBootTest(properties = {
        "skillmap.home=${java.io.tmpdir}/skillmap-cloud-test",
        "logging.level.org.springframework.web.servlet.mvc.method.annotation=DEBUG"
})
@ActiveProfiles("cloud")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CloudProfileTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Autowired
    FakeExtractor extractor;

    @BeforeEach
    void resetFake() {
        extractor.calls.set(0);
    }

    @Test
    void loadsOnlyTheAiService() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AuthService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AuthenticationFilter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(JobSyncScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DesktopLauncher.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DataSeeder.class)).isEmpty();
    }

    @Test
    void healthIsPublicAndDoesNotUseTheAi() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        assertThat(extractor.calls).hasValue(0);
    }

    @Test
    void extractsSkillsWithoutToken() throws Exception {
        extract("10.0.0.1", "{\"jobDescription\":\"Buscamos dev Java con Docker\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.skills[1]").value("Docker"))
                .andExpect(jsonPath("$.engine").value("gemini"));
    }

    @Test
    void allowsTwentyRequestsPerIpAndRejectsTheTwentyFirst() throws Exception {
        String body = "{\"jobDescription\":\"Oferta Java\"}";
        for (int i = 0; i < 20; i++) {
            extract("10.0.0.2", body).andExpect(status().isOk());
        }

        extract("10.0.0.2", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("20 análisis")));
        assertThat(extractor.calls).hasValue(20);

        // Otra IP no se ve afectada.
        extract("10.0.0.3", body).andExpect(status().isOk());
    }

    @Test
    void rejectsEmptyDescriptionWithoutSpendingQuota() throws Exception {
        extract("10.0.0.4", "{\"jobDescription\":\"\"}").andExpect(status().isBadRequest());
        extract("10.0.0.4", "{\"jobDescription\":\"   \"}").andExpect(status().isBadRequest());
        extract("10.0.0.4", "{}").andExpect(status().isBadRequest());
        extract("10.0.0.4", "no es json").andExpect(status().isBadRequest());

        assertThat(extractor.calls).hasValue(0);
    }

    @Test
    void rejectsTooLongDescription() throws Exception {
        String tooLong = "a".repeat(8_001);

        extract("10.0.0.5", "{\"jobDescription\":\"" + tooLong + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("8.000")));
        extract("10.0.0.5", "{\"jobDescription\":\"" + "a".repeat(8_000) + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void rejectsHugeBodyBeforeReadingIt() throws Exception {
        extract("10.0.0.6", "{\"jobDescription\":\"" + "a".repeat(70_000) + "\"}")
                .andExpect(status().isPayloadTooLarge());

        assertThat(extractor.calls).hasValue(0);
    }

    @Test
    void allowsAnyOriginOnlyForAiAndHealth() throws Exception {
        mvc.perform(options("/api/ai/extract-skills")
                        .header(HttpHeaders.ORIGIN, "http://localhost:51234")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
        mvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, "http://localhost:51234"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
        mvc.perform(get("/api/skills").header(HttpHeaders.ORIGIN, "http://localhost:51234"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void neverLogsTheJobDescription(CapturedOutput output) throws Exception {
        String marker = "OFERTA-SECRETA-NO-LOGUEAR-7731";

        extract("10.0.0.7", "{\"jobDescription\":\"" + marker + "\"}").andExpect(status().isOk());
        extract("10.0.0.7", "{\"jobDescription\":\"" + marker + "x".repeat(8_000) + "\"}")
                .andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain(marker);
    }

    private ResultActions extract(String ip, String body) throws Exception {
        return mvc.perform(post("/api/ai/extract-skills")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @TestConfiguration
    static class FakeExtractorConfig {
        @Bean
        @Primary
        FakeExtractor fakeExtractor() {
            return new FakeExtractor();
        }
    }

    /** Sustituye a Gemini: devuelve siempre lo mismo y cuenta las llamadas. */
    static class FakeExtractor implements JobSkillExtractor {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<String> extractSkills(String jobDescription, Collection<String> knownSkills) {
            calls.incrementAndGet();
            return List.of("Java", "Docker");
        }
    }
}
