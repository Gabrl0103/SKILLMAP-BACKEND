package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.model.ReadinessSnapshot;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.ReadinessSnapshotRepository;
import com.skillmap.api.infrastructure.persistence.ReadinessSnapshotJpaRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/goals/{id}/readiness/history y los snapshots que guardan PUT status, PATCH/DELETE master y PUT goal.
 * La IA está simulada, las fuentes de ofertas apuntan a un puerto cerrado y la base es en memoria.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-history-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-history-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "jobs.remotive.base-url=http://127.0.0.1:9",
        "jobs.arbeitnow.base-url=http://127.0.0.1:9",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
class ReadinessHistoryTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReadinessSnapshotRepository snapshots;

    @Autowired
    ReadinessSnapshotJpaRepository snapshotsTable;

    @MockBean
    JobSkillExtractor extractor;

    private String alice;
    private Long aliceId;
    private String bob;
    private Long bobId;
    private long goalId;
    private List<Long> goalSkills;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        alice = TestAuth.register(mvc);
        aliceId = userId(alice);
        bob = TestAuth.register(mvc);
        bobId = userId(bob);
        String json = mvc.perform(get("/api/goals").with(TestAuth.bearer(alice)))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> goal = ((List<Map<String, Object>>) JsonPath.read(json, "$")).get(0);
        goalId = ((Number) goal.get("id")).longValue();
        goalSkills = ((List<Map<String, Object>>) goal.get("skills")).stream()
                .map(s -> ((Number) s.get("skillId")).longValue()).toList();
        assertThat(goalSkills).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void everyChangeUpdatesTodaysSnapshotWithTheCurrentReadiness() throws Exception {
        long first = goalSkills.get(0);
        long second = goalSkills.get(1);

        // Elegir el objetivo guarda el punto de partida.
        putGoal(alice, goalId).andExpect(status().isOk());
        assertTodayMatchesReadiness(0);

        putStatus(alice, first, "MASTERED").andExpect(status().isOk());
        int afterFirst = assertTodayMatchesReadiness(-1);
        assertThat(afterFirst).isGreaterThan(0);

        mvc.perform(patch("/api/skills/" + second + "/master").with(TestAuth.bearer(alice))).andExpect(status().isOk());
        assertThat(assertTodayMatchesReadiness(-1)).isGreaterThan(afterFirst);

        mvc.perform(delete("/api/skills/" + second + "/master").with(TestAuth.bearer(alice))).andExpect(status().isOk());
        assertThat(assertTodayMatchesReadiness(-1)).isEqualTo(afterFirst);

        putStatus(alice, first, "IN_PROGRESS").andExpect(status().isOk());
        assertTodayMatchesReadiness(0);

        // Todo pasó hoy: un solo registro para este objetivo.
        assertThat(snapshots.findSince(aliceId, goalId, today().minusDays(365))).hasSize(1);
    }

    @Test
    void unchangedReadinessDoesNotAddSnapshots() throws Exception {
        long skill = goalSkills.get(0);
        snapshots.save(snapshot(aliceId, goalId, 0, today().minusDays(1)));
        long before = rowsOf(aliceId, goalId);

        // Aprendiendo no cuenta para el %, y quitar una marca que no existe no cambia nada.
        putStatus(alice, skill, "IN_PROGRESS").andExpect(status().isOk());
        mvc.perform(delete("/api/skills/" + goalSkills.get(1) + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk());
        putStatus(alice, skill, "PENDING").andExpect(status().isOk());

        assertThat(rowsOf(aliceId, goalId)).isEqualTo(before);
        history(alice, goalId, "").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(today().minusDays(1).toString()));
    }

    @Test
    void pointsOfDifferentDaysComeOrderedByDateWithinTheRequestedDays() throws Exception {
        // Guardados fuera de orden, como si fueran de días anteriores.
        snapshots.save(snapshot(aliceId, goalId, 20, today().minusDays(2)));
        snapshots.save(snapshot(aliceId, goalId, 5, today().minusDays(40)));
        snapshots.save(snapshot(aliceId, goalId, 10, today().minusDays(5)));

        putStatus(alice, goalSkills.get(0), "MASTERED").andExpect(status().isOk());
        int now = readiness(alice, goalId);

        history(alice, goalId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value(today().minusDays(5).toString()))
                .andExpect(jsonPath("$[0].percentage").value(10))
                .andExpect(jsonPath("$[1].date").value(today().minusDays(2).toString()))
                .andExpect(jsonPath("$[1].percentage").value(20))
                .andExpect(jsonPath("$[2].date").value(today().toString()))
                .andExpect(jsonPath("$[2].percentage").value(now));
        history(alice, goalId, "?days=3")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].percentage").value(20));
        history(alice, goalId, "?days=365")
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].percentage").value(5));
    }

    @Test
    void userWithoutHistoryGetsTheCurrentStateAsStartingPoint() throws Exception {
        // Progreso guardado antes de que existiera el historial.
        putStatus(alice, goalSkills.get(0), "MASTERED").andExpect(status().isOk());
        snapshots.deleteByUserId(aliceId);
        int now = readiness(alice, goalId);

        history(alice, goalId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(today().toString()))
                .andExpect(jsonPath("$[0].percentage").value(now));
        history(alice, goalId, "").andExpect(jsonPath("$.length()").value(1));
        assertThat(rowsOf(aliceId, goalId)).isEqualTo(1);
    }

    @Test
    void eachUserOnlySeesTheirOwnHistory() throws Exception {
        putStatus(alice, goalSkills.get(0), "MASTERED").andExpect(status().isOk());
        assertThat(rowsOf(bobId)).isZero();

        history(bob, goalId, "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].percentage").value(0));
        history(alice, goalId, "")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].percentage").value(greaterThan(0)));
    }

    @Test
    void requiresSession() throws Exception {
        mvc.perform(get("/api/goals/" + goalId + "/readiness/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sesión no válida o expirada. Inicia sesión de nuevo."));
        mvc.perform(get("/api/goals/" + goalId + "/readiness/history").with(TestAuth.bearer("token-inventado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownGoalIsNotFound() throws Exception {
        history(alice, 999999L, "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el objetivo con id 999999"));
        assertThat(rowsOf(aliceId)).isZero();
    }

    @Test
    void invalidDaysIsBadRequest() throws Exception {
        for (String days : List.of("0", "-3", "366")) {
            history(alice, goalId, "?days=" + days)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("El parámetro 'days' debe estar entre 1 y 365."));
        }
        history(alice, goalId, "?days=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'days' no tiene un valor válido."));
        history(alice, goalId, "?days=1").andExpect(status().isOk());
        history(alice, goalId, "?days=365").andExpect(status().isOk());
    }

    @Test
    void deletingTheAccountRemovesItsHistoryOnly() throws Exception {
        putStatus(alice, goalSkills.get(0), "MASTERED").andExpect(status().isOk());
        putStatus(bob, goalSkills.get(0), "MASTERED").andExpect(status().isOk());
        assertThat(rowsOf(aliceId)).isPositive();

        mvc.perform(delete("/api/auth/me").with(TestAuth.bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + TestAuth.PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(rowsOf(aliceId)).isZero();
        assertThat(rowsOf(bobId)).isPositive();
    }

    /** El último punto de hoy coincide con /readiness (y con expected si no es -1). Devuelve ese %. */
    private int assertTodayMatchesReadiness(int expected) throws Exception {
        int now = readiness(alice, goalId);
        if (expected >= 0) {
            assertThat(now).isEqualTo(expected);
        }
        String json = history(alice, goalId, "").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> points = JsonPath.read(json, "$");
        Map<String, Object> last = points.get(points.size() - 1);
        assertThat(last.get("date")).isEqualTo(today().toString());
        assertThat(last.get("percentage")).isEqualTo(now);
        return now;
    }

    private ResultActions history(String token, long goal, String query) throws Exception {
        return mvc.perform(get("/api/goals/" + goal + "/readiness/history" + query).with(TestAuth.bearer(token)));
    }

    private ResultActions putStatus(String token, long skillId, String status) throws Exception {
        return mvc.perform(put("/api/skills/" + skillId + "/status").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions putGoal(String token, long goal) throws Exception {
        return mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goal + "}"));
    }

    private int readiness(String token, long goal) throws Exception {
        String json = mvc.perform(get("/api/goals/" + goal + "/readiness").with(TestAuth.bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.readinessPercentage");
    }

    private Long userId(String token) throws Exception {
        String json = mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long rowsOf(Long userId) {
        return snapshotsTable.findAll().stream().filter(s -> s.getUserId().equals(userId)).count();
    }

    private long rowsOf(Long userId, long goal) {
        return snapshotsTable.findAll().stream()
                .filter(s -> s.getUserId().equals(userId) && s.getGoalId() == goal).count();
    }

    /** Mismo día que usa el servicio: la zona horaria del equipo. */
    private static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    private static ReadinessSnapshot snapshot(Long userId, long goal, int percentage, LocalDate day) {
        return new ReadinessSnapshot(null, userId, goal, percentage, day, Instant.now());
    }
}
