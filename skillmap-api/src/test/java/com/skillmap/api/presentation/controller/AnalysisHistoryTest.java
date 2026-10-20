package com.skillmap.api.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.exception.AiServiceException;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.infrastructure.persistence.AnalysisHistoryJpaRepository;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Historial del Analizador (GET/DELETE /api/analyzer/history) y su caché por usuario. La IA está simulada,
 * las fuentes de ofertas apuntan a un puerto cerrado y la base es en memoria: nunca se llama a una API externa.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-analysis-history-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-analysis-history-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "jobs.remotive.base-url=http://127.0.0.1:9",
        "jobs.arbeitnow.base-url=http://127.0.0.1:9",
        // Como en la app: el SQL a la consola. Solo lleva "?", nunca el texto de la oferta.
        "spring.jpa.show-sql=true",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AnalysisHistoryTest {

    private static final String HISTORY = "/api/analyzer/history";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    SkillRepository skillRepository;

    @Autowired
    AnalysisHistoryJpaRepository historyTable;

    @MockBean
    JobSkillExtractor extractor;

    private String alice;
    private String bob;
    private long reactId;

    @BeforeEach
    void setUp() throws Exception {
        alice = TestAuth.register(mvc);
        bob = TestAuth.register(mvc);
        reactId = skillRepository.findAll().stream()
                .filter(s -> s.getName().equals("React")).findFirst().orElseThrow().getId();
        when(extractor.engine()).thenReturn("gemini");
        when(extractor.extractSkills(anyString(), any())).thenReturn(List.of("React", "Kafka"));
    }

    // ---------- Se guarda solo lo que sale bien ----------

    @Test
    void aSuccessfulAnalysisIsSavedAndListedWithoutItsText() throws Exception {
        long id = historyIdOf(analyze(alice, "Desarrollador Frontend\nBuscamos React y Kafka")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("gemini"))
                .andExpect(jsonPath("$.missingSkills", hasSize(2))));

        mvc.perform(get(HISTORY).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].title").value("Desarrollador Frontend"))
                .andExpect(jsonPath("$[0].engine").value("gemini"))
                .andExpect(jsonPath("$[0].matchPercentage").value(0))
                .andExpect(jsonPath("$[0].skillCount").value(2))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].jobDescription").doesNotExist());
    }

    @Test
    void failedAnalysesAreNotSaved() throws Exception {
        when(extractor.extractSkills(anyString(), any())).thenThrow(new AiServiceException("El servicio de IA no está disponible."));

        analyze(alice, "Buscamos React").andExpect(status().isBadGateway());
        analyze(alice, "   ").andExpect(status().isBadRequest());
        analyze(alice, "a".repeat(8001)).andExpect(status().isBadRequest());

        mvc.perform(get(HISTORY).with(TestAuth.bearer(alice))).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void withoutTokenNothingIsSavedAndEveryEndpointAnswers401() throws Exception {
        long before = historyTable.count();
        mvc.perform(post("/api/analyzer/analyze").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobDescription\":\"Buscamos React\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get(HISTORY)).andExpect(status().isUnauthorized());
        mvc.perform(get(HISTORY + "/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete(HISTORY + "/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete(HISTORY)).andExpect(status().isUnauthorized());

        assertThat(historyTable.count()).isEqualTo(before);
        verify(extractor, never()).extractSkills(anyString(), any());
    }

    // ---------- Orden y límites ----------

    @Test
    void newestFirstAndLimitParameter() throws Exception {
        long first = historyIdOf(analyze(alice, "Oferta uno"));
        long second = historyIdOf(analyze(alice, "Oferta dos"));
        long third = historyIdOf(analyze(alice, "Oferta tres"));

        mvc.perform(get(HISTORY).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$[*].id", contains((int) third, (int) second, (int) first)));
        mvc.perform(get(HISTORY).param("limit", "2").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$[*].id", contains((int) third, (int) second)));
    }

    @Test
    void invalidLimitIs400InSpanish() throws Exception {
        for (String limit : List.of("0", "101", "-5")) {
            mvc.perform(get(HISTORY).param("limit", limit).with(TestAuth.bearer(alice)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("El parámetro 'limit' debe estar entre 1 y 100."));
        }
        mvc.perform(get(HISTORY).param("limit", "muchos").with(TestAuth.bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'limit' no tiene un valor válido."));
        mvc.perform(get(HISTORY).param("limit", "100").with(TestAuth.bearer(alice))).andExpect(status().isOk());
    }

    @Test
    void keepsAtMost100PerUserDroppingTheOldest() throws Exception {
        long oldest = historyIdOf(analyze(alice, "Oferta número 0"));
        long newest = 0;
        for (int i = 1; i <= 100; i++) {
            newest = historyIdOf(analyze(alice, "Oferta número " + i));
        }
        long bobsEntry = historyIdOf(analyze(bob, "La de Bob"));

        Long aliceId = historyTable.findById(newest).orElseThrow().getUserId();
        assertThat(historyTable.countByUserId(aliceId)).isEqualTo(100);
        assertThat(historyTable.findById(oldest)).isEmpty();
        mvc.perform(get(HISTORY).param("limit", "100").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$", hasSize(100)))
                .andExpect(jsonPath("$[0].title").value("Oferta número 100"))
                .andExpect(jsonPath("$[99].title").value("Oferta número 1"));
        assertThat(historyTable.findById(bobsEntry)).isPresent();
    }

    // ---------- Cada usuario ve y borra solo lo suyo ----------

    @Test
    void usersOnlySeeAndDeleteTheirOwn() throws Exception {
        long id = historyIdOf(analyze(alice, "Buscamos React"));

        mvc.perform(get(HISTORY).with(TestAuth.bearer(bob))).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(HISTORY + "/" + id).with(TestAuth.bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el análisis."));
        mvc.perform(delete(HISTORY + "/" + id).with(TestAuth.bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(delete(HISTORY).with(TestAuth.bearer(bob))).andExpect(status().isNoContent());

        mvc.perform(get(HISTORY + "/" + id).with(TestAuth.bearer(alice))).andExpect(status().isOk());
    }

    @Test
    void unknownOrInvalidIds() throws Exception {
        mvc.perform(get(HISTORY + "/999999").with(TestAuth.bearer(alice))).andExpect(status().isNotFound());
        mvc.perform(delete(HISTORY + "/999999").with(TestAuth.bearer(alice))).andExpect(status().isNotFound());
        mvc.perform(get(HISTORY + "/abc").with(TestAuth.bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'id' no tiene un valor válido."));
    }

    // ---------- Detalle recalculado ----------

    @Test
    void detailIsRecalculatedWithTheCurrentProgressWithoutCallingTheAi() throws Exception {
        long id = historyIdOf(analyze(alice, "Buscamos React y Kafka"));
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice))).andExpect(status().isOk());

        mvc.perform(get(HISTORY + "/" + id).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.jobDescription").value("Buscamos React y Kafka"))
                .andExpect(jsonPath("$.engine").value("gemini"))
                .andExpect(jsonPath("$.recalculated").value(true))
                .andExpect(jsonPath("$.savedMatchPercentage").value(0))
                .andExpect(jsonPath("$.matchPercentage").value(50))
                .andExpect(jsonPath("$.masteredSkills", contains("React")))
                .andExpect(jsonPath("$.missingSkills", contains("Kafka")));
        // El listado conserva el % de cuando se analizó.
        mvc.perform(get(HISTORY).with(TestAuth.bearer(alice))).andExpect(jsonPath("$[0].matchPercentage").value(0));
        verify(extractor, times(1)).extractSkills(anyString(), any());
    }

    // ---------- Borrado ----------

    @Test
    void deleteOneAndDeleteAll() throws Exception {
        long first = historyIdOf(analyze(alice, "Oferta uno"));
        long second = historyIdOf(analyze(alice, "Oferta dos"));
        long bobs = historyIdOf(analyze(bob, "Oferta de Bob"));

        mvc.perform(delete(HISTORY + "/" + first).with(TestAuth.bearer(alice))).andExpect(status().isNoContent());
        mvc.perform(delete(HISTORY + "/" + first).with(TestAuth.bearer(alice))).andExpect(status().isNotFound());
        mvc.perform(get(HISTORY).with(TestAuth.bearer(alice))).andExpect(jsonPath("$[*].id", contains((int) second)));

        mvc.perform(delete(HISTORY).with(TestAuth.bearer(alice))).andExpect(status().isNoContent());
        mvc.perform(get(HISTORY).with(TestAuth.bearer(alice))).andExpect(jsonPath("$").isEmpty());
        assertThat(historyTable.findById(bobs)).isPresent();
    }

    @Test
    void deletingTheAccountDeletesItsHistory() throws Exception {
        long id = historyIdOf(analyze(alice, "Buscamos React"));
        long bobs = historyIdOf(analyze(bob, "Buscamos React"));
        Long aliceId = historyTable.findById(id).orElseThrow().getUserId();

        mvc.perform(delete("/api/auth/me").with(TestAuth.bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + TestAuth.PASSWORD + "\"}")).andExpect(status().isNoContent());

        assertThat(historyTable.countByUserId(aliceId)).isZero();
        assertThat(historyTable.findById(bobs)).isPresent();
    }

    // ---------- Caché por usuario ----------

    @Test
    void sameOfferAgainUsesTheCacheAndTheCurrentProgress() throws Exception {
        analyze(alice, "Buscamos React\ny Kafka")
                .andExpect(jsonPath("$.engine").value("gemini"))
                .andExpect(jsonPath("$.matchPercentage").value(0));
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice))).andExpect(status().isOk());

        long cached = historyIdOf(analyze(alice, "  Buscamos   React y Kafka ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("cache"))
                .andExpect(jsonPath("$.matchPercentage").value(50))
                .andExpect(jsonPath("$.masteredSkills", contains("React"))));
        verify(extractor, times(1)).extractSkills(anyString(), any());

        // Una sola entrada por oferta, la del último análisis.
        mvc.perform(get(HISTORY).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$[*].id", contains((int) cached)))
                .andExpect(jsonPath("$[0].engine").value("cache"))
                .andExpect(jsonPath("$[0].matchPercentage").value(50));

        // La caché no se comparte: Bob con el mismo texto sí llama a la IA.
        analyze(bob, "Buscamos React\ny Kafka").andExpect(jsonPath("$.engine").value("gemini"));
        verify(extractor, times(2)).extractSkills(anyString(), any());
    }

    @Test
    void deletingTheHistoryAlsoForgetsTheCache() throws Exception {
        analyze(alice, "Buscamos React");
        mvc.perform(delete(HISTORY).with(TestAuth.bearer(alice))).andExpect(status().isNoContent());

        analyze(alice, "Buscamos React").andExpect(jsonPath("$.engine").value("gemini"));
        verify(extractor, times(2)).extractSkills(anyString(), any());
    }

    // ---------- Logs ----------

    @Test
    void theOfferTextNeverReachesTheLogs(CapturedOutput output) throws Exception {
        String marker = "oferta-secreta-" + UUID.randomUUID();
        long id = historyIdOf(analyze(alice, "Buscamos React " + marker));
        analyze(alice, "Buscamos React " + marker).andExpect(jsonPath("$.engine").value("cache"));
        mvc.perform(get(HISTORY + "/" + id).with(TestAuth.bearer(alice)));
        when(extractor.extractSkills(anyString(), any())).thenThrow(new AiServiceException("El servicio de IA no está disponible."));
        analyze(alice, "Otra oferta " + marker).andExpect(status().isBadGateway());

        assertThat(output.getAll()).doesNotContain(marker);
    }

    private ResultActions analyze(String token, String text) throws Exception {
        String body = "{\"jobDescription\":" + JSON.writeValueAsString(text) + "}";
        return mvc.perform(post("/api/analyzer/analyze").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long historyIdOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.historyId");
        return id.longValue();
    }
}
