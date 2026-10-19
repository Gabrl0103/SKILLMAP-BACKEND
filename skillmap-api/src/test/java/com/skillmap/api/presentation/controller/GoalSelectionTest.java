package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/auth/me/goal: el objetivo profesional es del usuario y cambiarlo no toca su progreso.
 * La IA está simulada, las fuentes de ofertas apuntan a un puerto cerrado y la base es en memoria.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-goal-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-goal-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "jobs.remotive.base-url=http://127.0.0.1:9",
        "jobs.arbeitnow.base-url=http://127.0.0.1:9",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
class GoalSelectionTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    JobSkillExtractor extractor;

    private String alice;
    private String bob;
    private List<Map<String, Object>> goals;

    @BeforeEach
    void setUp() throws Exception {
        alice = TestAuth.register(mvc);
        bob = TestAuth.register(mvc);
        String json = mvc.perform(get("/api/goals").with(TestAuth.bearer(alice)))
                .andReturn().getResponse().getContentAsString();
        goals = JsonPath.read(json, "$");
        assertThat(goals).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void newUserHasNoGoalUntilChoosingOne() throws Exception {
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").doesNotExist());

        long goalId = goalId(0);
        putGoal(alice, "{\"goalId\":" + goalId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.email").exists());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.goalId").value(goalId));
    }

    @Test
    void goalSurvivesANewLogin() throws Exception {
        String email = "goal-" + UUID.randomUUID() + "@test.local";
        String token = TestAuth.register(mvc, email);
        long goalId = goalId(1);
        putGoal(token, "{\"goalId\":" + goalId + "}").andExpect(status().isOk());

        String body = "{\"email\":\"" + email + "\",\"password\":\"" + TestAuth.PASSWORD + "\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.goalId").value(goalId));
    }

    @Test
    void changingGoalKeepsSkillProgressAndSharedSkillsCountInBoth() throws Exception {
        long[] pair = goalsSharingASkill();
        long shared = sharedSkill(pair[0], pair[1]);

        putGoal(alice, "{\"goalId\":" + pair[0] + "}").andExpect(status().isOk());
        mvc.perform(put("/api/skills/" + shared + "/status").with(TestAuth.bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MASTERED\"}"))
                .andExpect(status().isOk());
        int before = masteredCount(alice, pair[0]);

        putGoal(alice, "{\"goalId\":" + pair[1] + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(pair[1]));

        // El progreso es por usuario, no por objetivo.
        mvc.perform(get("/api/skills/" + shared).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.status").value("MASTERED"));
        assertThat(masteredCount(alice, pair[1])).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/goals/" + pair[1] + "/readiness").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.readinessPercentage").value(greaterThan(0)))
                .andExpect(jsonPath("$.gaps[?(@.id == " + shared + ")]").isEmpty());

        // Volver al objetivo anterior lo deja como estaba.
        putGoal(alice, "{\"goalId\":" + pair[0] + "}").andExpect(status().isOk());
        assertThat(masteredCount(alice, pair[0])).isEqualTo(before);
    }

    @Test
    void unknownGoalIsNotFound() throws Exception {
        putGoal(alice, "{\"goalId\":999999}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el objetivo con id 999999"));
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.goalId").doesNotExist());
    }

    @Test
    void missingOrInvalidGoalIsBadRequest() throws Exception {
        long goalId = goalId(0);
        putGoal(alice, "{\"goalId\":" + goalId + "}");

        putGoal(alice, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elige un objetivo profesional."));
        putGoal(alice, "{\"goalId\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elige un objetivo profesional."));
        mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elige un objetivo profesional."));
        putGoal(alice, "{\"goalId\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El campo 'goalId' no tiene un valor válido."));

        // Nada de lo anterior cambió el objetivo guardado.
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.goalId").value(goalId));
    }

    @Test
    void requiresSession() throws Exception {
        String body = "{\"goalId\":" + goalId(0) + "}";
        mvc.perform(put("/api/auth/me/goal").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer("token-inventado"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachUserKeepsTheirOwnGoal() throws Exception {
        putGoal(alice, "{\"goalId\":" + goalId(0) + "}");
        putGoal(bob, "{\"goalId\":" + goalId(1) + "}");

        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.goalId").value(goalId(0)));
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(bob)))
                .andExpect(jsonPath("$.goalId").value(goalId(1)));
    }

    private ResultActions putGoal(String token, String body) throws Exception {
        return mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long goalId(int index) {
        return ((Number) goals.get(index).get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Set<Long> skillIds(long goalId) {
        Map<String, Object> goal = goals.stream()
                .filter(g -> ((Number) g.get("id")).longValue() == goalId).findFirst().orElseThrow();
        Set<Long> ids = new HashSet<>();
        for (Map<String, Object> s : (List<Map<String, Object>>) goal.get("skills")) {
            ids.add(((Number) s.get("skillId")).longValue());
        }
        return ids;
    }

    private long[] goalsSharingASkill() {
        for (int i = 0; i < goals.size(); i++) {
            for (int j = i + 1; j < goals.size(); j++) {
                Set<Long> common = new HashSet<>(skillIds(goalId(i)));
                common.retainAll(skillIds(goalId(j)));
                if (!common.isEmpty()) {
                    return new long[] {goalId(i), goalId(j)};
                }
            }
        }
        throw new AssertionError("El seed no tiene dos objetivos con una habilidad en común");
    }

    private long sharedSkill(long a, long b) {
        Set<Long> common = new HashSet<>(skillIds(a));
        common.retainAll(skillIds(b));
        return common.iterator().next();
    }

    private int masteredCount(String token, long goalId) throws Exception {
        String json = mvc.perform(get("/api/goals/" + goalId + "/readiness").with(TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.masteredCount");
    }
}
