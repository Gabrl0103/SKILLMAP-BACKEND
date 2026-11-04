package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * statusUpdatedAt de /api/skills: cuándo pasó el usuario al estado actual (las rachas de Mi Ruta cuentan avances por
 * semana con esta fecha). El reloj está fijado; la IA está simulada, las fuentes de ofertas apuntan a un puerto cerrado
 * y la base es en memoria.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-status-date-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-status-date-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "jobs.remotive.base-url=http://127.0.0.1:9",
        "jobs.arbeitnow.base-url=http://127.0.0.1:9",
        "skillmap.ai.remote-url=http://127.0.0.1:9",
        "skillmap.sync.remotive.enabled=false",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
@Import(SkillStatusDateTest.FixedClock.class)
class SkillStatusDateTest {

    /** Con fracción de segundo a propósito: la fecha se guarda y se devuelve al segundo. */
    private static final Instant MONDAY = Instant.parse("2026-11-02T09:30:00.123456789Z");
    private static final String MONDAY_JSON = "2026-11-02T09:30:00Z";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(MONDAY);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    SkillRepository skillRepository;

    @Autowired
    MutableClock clock;

    @MockBean
    JobSkillExtractor extractor;

    private String alice;
    private long reactId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY);
        alice = TestAuth.register(mvc);
        reactId = skillRepository.findAll().stream()
                .filter(s -> s.getName().equals("React")).findFirst().map(Skill::getId).orElseThrow();
    }

    @Test
    void pendingSkillsHaveNoDate() throws Exception {
        mvc.perform(get("/api/skills").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].statusUpdatedAt", everyItem(nullValue())));
    }

    @Test
    void changingStateRecordsWhenItHappenedEverywhereTheSkillIsReturned() throws Exception {
        putStatus(alice, reactId, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.statusUpdatedAt").value(MONDAY_JSON));

        mvc.perform(get("/api/skills/" + reactId).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.statusUpdatedAt").value(MONDAY_JSON));
        assertThat(dateInList(alice, reactId)).isEqualTo(MONDAY_JSON);

        // Las brechas del objetivo son las mismas habilidades: traen la fecha también
        String goals = mvc.perform(get("/api/goals").with(TestAuth.bearer(alice)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(goals, "$[?(@.title == 'Frontend Senior')].id");
        mvc.perform(get("/api/goals/" + ids.get(0) + "/readiness").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.gaps[?(@.name == 'React')].statusUpdatedAt").value(MONDAY_JSON));
    }

    @Test
    void repeatingTheSameStateKeepsTheDateAndANewStateMovesIt() throws Exception {
        putStatus(alice, reactId, "IN_PROGRESS");
        clock.advance(Duration.ofHours(3));
        putStatus(alice, reactId, "IN_PROGRESS").andExpect(jsonPath("$.statusUpdatedAt").value(MONDAY_JSON));
        assertThat(dateInList(alice, reactId)).isEqualTo(MONDAY_JSON);

        clock.advance(Duration.ofDays(2)); // miércoles 12:30
        putStatus(alice, reactId, "MASTERED").andExpect(jsonPath("$.statusUpdatedAt").value("2026-11-04T12:30:00Z"));

        // PATCH /master sobre una ya dominada tampoco la mueve
        clock.advance(Duration.ofDays(1));
        mvc.perform(patch("/api/skills/" + reactId + "/master").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusUpdatedAt").value("2026-11-04T12:30:00Z"));

        // Dominada -> aprendiendo también es un cambio
        putStatus(alice, reactId, "IN_PROGRESS").andExpect(jsonPath("$.statusUpdatedAt").value("2026-11-05T12:30:00Z"));
    }

    @Test
    void backToPendingClearsTheDateAndStartingAgainGetsANewOne() throws Exception {
        putStatus(alice, reactId, "MASTERED");
        putStatus(alice, reactId, "PENDING")
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusUpdatedAt").value(nullValue()));
        assertThat(dateInList(alice, reactId)).isNull();

        clock.advance(Duration.ofDays(7));
        putStatus(alice, reactId, "IN_PROGRESS").andExpect(jsonPath("$.statusUpdatedAt").value("2026-11-09T09:30:00Z"));
    }

    @Test
    void eachUserHasTheirOwnDate() throws Exception {
        String bob = TestAuth.register(mvc);
        putStatus(alice, reactId, "IN_PROGRESS");
        clock.advance(Duration.ofDays(1));
        putStatus(bob, reactId, "IN_PROGRESS");

        assertThat(dateInList(alice, reactId)).isEqualTo(MONDAY_JSON);
        assertThat(dateInList(bob, reactId)).isEqualTo("2026-11-03T09:30:00Z");
    }

    // ---------- Ayudas ----------

    private ResultActions putStatus(String token, long skillId, String state) throws Exception {
        return mvc.perform(put("/api/skills/" + skillId + "/status").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + state + "\"}"));
    }

    private String dateInList(String token, long skillId) throws Exception {
        String body = mvc.perform(get("/api/skills").with(TestAuth.bearer(token)))
                .andReturn().getResponse().getContentAsString();
        List<String> dates = JsonPath.read(body, "$[?(@.id == " + skillId + ")].statusUpdatedAt");
        return dates.get(0);
    }

    /** Reloj que el test mueve a mano (cada test vuelve al lunes). Lo usan todos los servicios, sesiones incluidas. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
