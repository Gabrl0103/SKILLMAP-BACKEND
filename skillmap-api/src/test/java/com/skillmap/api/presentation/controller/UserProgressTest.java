package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Progreso por usuario: habilidades, readiness y Analizador usan el del usuario de la sesión.
 * La IA está simulada (nunca se llama a Gemini) y la base es en memoria.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-progress-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-progress-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
class UserProgressTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SkillRepository skillRepository;

    @MockBean
    JobSkillExtractor extractor;

    private String alice;
    private String bob;
    private long reactId;

    @BeforeEach
    void setUp() throws Exception {
        alice = TestAuth.register(mvc);
        bob = TestAuth.register(mvc);
        Skill react = skillRepository.findAll().stream()
                .filter(s -> s.getName().equals("React")).findFirst().orElseThrow();
        reactId = react.getId();
        // Como en una base anterior a los usuarios: la fila global dice "dominada".
        skillRepository.save(react.withStatus(SkillStatus.MASTERED));
    }

    @Test
    void newUserStartsWithoutMasteredSkills() throws Exception {
        mvc.perform(get("/api/skills").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$[?(@.status == 'MASTERED')]").isEmpty());
        mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void masteringASkillOnlyAffectsThatUser() throws Exception {
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MASTERED"))
                .andExpect(jsonPath("$.name").value("React"));

        mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.status").value("MASTERED"));
        mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(bob)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/skills").with(TestAuth.bearer(bob)))
                .andExpect(jsonPath("$[?(@.status == 'MASTERED')]").isEmpty());

        // Dominarla dos veces no duplica nada.
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void unmasteringASkillOnlyAffectsThatUser() throws Exception {
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)));
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(bob)));

        mvc.perform(delete("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.name").value("React"));

        mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(bob)))
                .andExpect(jsonPath("$.status").value("MASTERED"));
    }

    @Test
    void unmasteringIsIdempotent() throws Exception {
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)));
        mvc.perform(delete("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        // Nunca marcada: también responde bien.
        mvc.perform(delete("/api/skills/" + reactId + "/master").with(TestAuth.bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void unmasteringAnUnknownSkillIsNotFound() throws Exception {
        mvc.perform(delete("/api/skills/999999/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unmasteringRequiresSession() throws Exception {
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)));
        mvc.perform(delete("/api/skills/" + reactId + "/master"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/skills/" + reactId + "/master").with(TestAuth.bearer("token-inventado")))
                .andExpect(status().isUnauthorized());
        // El intento sin sesión no tocó el progreso.
        mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.status").value("MASTERED"));
    }

    @Test
    void demandIsTheSameForEveryone() throws Exception {
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)));
        int aliceDemand = demandOf(alice);
        assertThat(demandOf(bob)).isEqualTo(aliceDemand);
    }

    @Test
    void readinessUsesTheUsersProgress() throws Exception {
        String goals = mvc.perform(get("/api/goals").with(TestAuth.bearer(alice)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(goals, "$[?(@.title == 'Frontend Senior')].id");
        String readiness = "/api/goals/" + ids.get(0) + "/readiness";

        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)));

        mvc.perform(get(readiness).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteredCount").value(1))
                .andExpect(jsonPath("$.readinessPercentage").value(28)); // 5 de 18
        mvc.perform(get(readiness).with(TestAuth.bearer(bob)))
                .andExpect(jsonPath("$.masteredCount").value(0))
                .andExpect(jsonPath("$.readinessPercentage").value(0));
    }

    @Test
    void analyzerUsesTheUsersProgress() throws Exception {
        when(extractor.extractSkills(anyString(), any())).thenReturn(List.of("React"));
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)));

        String body = "{\"jobDescription\":\"Buscamos React\"}";
        mvc.perform(post("/api/analyzer/analyze").with(TestAuth.bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteredSkills[0]").value("React"));
        mvc.perform(post("/api/analyzer/analyze").with(TestAuth.bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteredSkills").isEmpty())
                .andExpect(jsonPath("$.missingSkills[0]").value("React"));
    }

    private int demandOf(String token) throws Exception {
        String json = mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.demandPercentage");
    }
}
