package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.event.SkillMasteredEvent;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/skills/{id}/status: pendiente, aprendiendo o dominada, solo para el usuario de la sesión.
 * La IA está simulada, las fuentes de ofertas apuntan a un puerto cerrado y la base es en memoria.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-status-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-status-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "jobs.remotive.base-url=http://127.0.0.1:9",
        "jobs.arbeitnow.base-url=http://127.0.0.1:9",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
@RecordApplicationEvents
class SkillStatusTest {

    private static final String INVALID_MESSAGE = "El estado no es válido. Valores permitidos: PENDING, IN_PROGRESS, MASTERED.";

    @Autowired
    MockMvc mvc;

    @Autowired
    SkillRepository skillRepository;

    @Autowired
    ApplicationEvents events;

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
                .filter(s -> s.getName().equals("React")).findFirst().map(Skill::getId).orElseThrow();
    }

    @Test
    void eachStateIsSavedAndReturned() throws Exception {
        for (String state : List.of("IN_PROGRESS", "MASTERED", "PENDING")) {
            putStatus(alice, reactId, state)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reactId))
                    .andExpect(jsonPath("$.name").value("React"))
                    .andExpect(jsonPath("$.status").value(state));
            assertThat(statusOf(alice, reactId)).isEqualTo(state);
        }
    }

    @Test
    void masteredCanGoBackToInProgressAndThenToPending() throws Exception {
        putStatus(alice, reactId, "MASTERED").andExpect(status().isOk());

        putStatus(alice, reactId, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        assertThat(statusOf(alice, reactId)).isEqualTo("IN_PROGRESS");

        putStatus(alice, reactId, "PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(statusOf(alice, reactId)).isEqualTo("PENDING");

        // Repetir un estado no falla ni duplica nada.
        putStatus(alice, reactId, "PENDING").andExpect(status().isOk());
        putStatus(alice, reactId, "IN_PROGRESS").andExpect(status().isOk());
        putStatus(alice, reactId, "IN_PROGRESS").andExpect(status().isOk());
        assertThat(statusOf(alice, reactId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void unknownOrMissingStateIsBadRequest() throws Exception {
        putStatus(alice, reactId, "LEARNING")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
        putStatus(alice, reactId, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
        putJson(alice, reactId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
        putJson(alice, reactId, "{\"status\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
        mvc.perform(put("/api/skills/" + reactId + "/status").with(TestAuth.bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
        // Nada de lo anterior tocó el progreso.
        assertThat(statusOf(alice, reactId)).isEqualTo("PENDING");
    }

    @Test
    void unknownSkillIsNotFound() throws Exception {
        for (String state : List.of("PENDING", "IN_PROGRESS", "MASTERED")) {
            putStatus(alice, 999999, state)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No se encontró la habilidad con id 999999"));
        }
    }

    @Test
    void requiresSession() throws Exception {
        String body = "{\"status\":\"MASTERED\"}";
        mvc.perform(put("/api/skills/" + reactId + "/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/skills/" + reactId + "/status").with(TestAuth.bearer("token-inventado"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(statusOf(alice, reactId)).isEqualTo("PENDING");
    }

    @Test
    void onlyChangesTheUsersProgress() throws Exception {
        putStatus(bob, reactId, "MASTERED");
        putStatus(alice, reactId, "IN_PROGRESS");

        assertThat(statusOf(alice, reactId)).isEqualTo("IN_PROGRESS");
        assertThat(statusOf(bob, reactId)).isEqualTo("MASTERED");

        putStatus(alice, reactId, "PENDING");
        assertThat(statusOf(bob, reactId)).isEqualTo("MASTERED");

        // La fila global del catálogo no cambia: un usuario nuevo la ve pendiente.
        assertThat(statusOf(TestAuth.register(mvc), reactId)).isEqualTo("PENDING");
    }

    @Test
    void masteredEventIsPublishedOnlyWhenMastering() throws Exception {
        putStatus(alice, reactId, "IN_PROGRESS");
        putStatus(alice, reactId, "PENDING");
        assertThat(events.stream(SkillMasteredEvent.class)).isEmpty();

        putStatus(alice, reactId, "MASTERED");
        assertThat(events.stream(SkillMasteredEvent.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getSkillId()).isEqualTo(reactId);
                    assertThat(e.getSkillName()).isEqualTo("React");
                });

        putStatus(alice, reactId, "IN_PROGRESS");
        assertThat(events.stream(SkillMasteredEvent.class)).hasSize(1);
    }

    @Test
    void readinessOnlyCountsMasteredSkills() throws Exception {
        String goals = mvc.perform(get("/api/goals").with(TestAuth.bearer(alice)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(goals, "$[?(@.title == 'Frontend Senior')].id");
        String readiness = "/api/goals/" + ids.get(0) + "/readiness";

        putStatus(alice, reactId, "IN_PROGRESS");
        mvc.perform(get(readiness).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.masteredCount").value(0))
                .andExpect(jsonPath("$.readinessPercentage").value(0))
                // Aprendiendo sigue siendo brecha, con su estado.
                .andExpect(jsonPath("$.gaps[?(@.name == 'React')].status").value("IN_PROGRESS"));

        putStatus(alice, reactId, "MASTERED");
        mvc.perform(get(readiness).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.masteredCount").value(1))
                .andExpect(jsonPath("$.readinessPercentage").value(28))
                .andExpect(jsonPath("$.gaps[?(@.name == 'React')]").isEmpty());
    }

    @Test
    void masterEndpointsStillWorkAlongsideStatus() throws Exception {
        putStatus(alice, reactId, "IN_PROGRESS");
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MASTERED"));
        assertThat(statusOf(alice, reactId)).isEqualTo("MASTERED");

        putStatus(alice, reactId, "IN_PROGRESS");
        mvc.perform(delete("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(statusOf(alice, reactId)).isEqualTo("PENDING");
    }

    private ResultActions putStatus(String token, long skillId, String state) throws Exception {
        return putJson(token, skillId, "{\"status\":\"" + state + "\"}");
    }

    private ResultActions putJson(String token, long skillId, String body) throws Exception {
        return mvc.perform(put("/api/skills/" + skillId + "/status").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String statusOf(String token, long skillId) throws Exception {
        String json = mvc.perform(get("/api/skills/" + skillId).with(TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.status");
    }
}
