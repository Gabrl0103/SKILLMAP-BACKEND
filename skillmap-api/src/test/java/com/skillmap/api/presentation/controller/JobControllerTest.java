package com.skillmap.api.presentation.controller;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.infrastructure.jobs.ArbeitnowJobSource;
import com.skillmap.api.infrastructure.jobs.RemotiveJobSource;
import com.skillmap.api.infrastructure.persistence.JobPostingJpaRepository;
import com.skillmap.api.infrastructure.persistence.JobSourceSyncJpaRepository;
import com.skillmap.api.support.TestAuth;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/jobs/sync y /api/jobs/stats de punta a punta, con Arbeitnow y Remotive simulados (nunca se
 * llama a las APIs reales) y un reloj controlado para probar el intervalo mínimo de 6 h.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-jobs-controller-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-jobs-controller-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class JobControllerTest {

    private static final Instant T0 = Instant.parse("2026-10-02T12:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JobPostingJpaRepository postingsTable;

    @Autowired
    JobSourceSyncJpaRepository syncsTable;

    @MockBean
    ArbeitnowJobSource arbeitnow;

    @MockBean
    RemotiveJobSource remotive;

    @MockBean
    Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        postingsTable.deleteAll();
        syncsTable.deleteAll();
        when(arbeitnow.name()).thenReturn("Arbeitnow");
        when(remotive.name()).thenReturn("Remotive");
        when(arbeitnow.fetch()).thenReturn(postings("Arbeitnow", 3));
        when(remotive.fetch()).thenReturn(postings("Remotive", 2));
        when(clock.instant()).thenReturn(T0);

        token = TestAuth.register(mvc);
    }

    private String token;

    /** Todas las peticiones con la sesión de un usuario recién registrado. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(TestAuth.bearer(token)));
    }

    @Test
    void syncReportsPerSourceUpdateAndCooldown() throws Exception {
        perform(post("/api/jobs/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(5))
                .andExpect(jsonPath("$.sources[?(@.source=='Arbeitnow')].status", contains("SYNCED")))
                .andExpect(jsonPath("$.sources[?(@.source=='Arbeitnow')].updated", contains(true)))
                .andExpect(jsonPath("$.sources[?(@.source=='Arbeitnow')].added", contains(3)))
                .andExpect(jsonPath("$.sources[?(@.source=='Remotive')].updated", contains(true)))
                .andExpect(jsonPath("$.sources[?(@.source=='Remotive')].nextSyncAt", contains("2026-10-02T18:00:00Z")))
                .andExpect(jsonPath("$.sources[?(@.source=='Remotive')].minutesUntilNextSync", contains(360)));

        // 2 h 30 min después: ninguna fuente se vuelve a consultar.
        when(clock.instant()).thenReturn(T0.plus(Duration.ofMinutes(150)));

        perform(post("/api/jobs/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[*].status", contains("SKIPPED", "SKIPPED")))
                .andExpect(jsonPath("$.sources[*].updated", contains(false, false)))
                .andExpect(jsonPath("$.sources[*].minutesUntilNextSync", contains(210, 210)))
                .andExpect(jsonPath("$.sources[?(@.source=='Arbeitnow')].lastSyncAt", contains("2026-10-02T12:00:00Z")));

        verify(arbeitnow, times(1)).fetch();
        verify(remotive, times(1)).fetch();
    }

    @Test
    void statsHaveTotalPerSourceCountsAndLastSyncInUtc() throws Exception {
        perform(get("/api/jobs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(0))
                .andExpect(jsonPath("$.sources[?(@.name=='Arbeitnow')].jobs", contains(0)))
                .andExpect(jsonPath("$.sources[?(@.name=='Remotive')].jobs", contains(0)))
                .andExpect(jsonPath("$.lastSyncAt").doesNotExist());

        perform(post("/api/jobs/sync")).andExpect(status().isOk());

        perform(get("/api/jobs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(5))
                .andExpect(jsonPath("$.sources[?(@.name=='Arbeitnow')].jobs", contains(3)))
                .andExpect(jsonPath("$.sources[?(@.name=='Remotive')].jobs", contains(2)))
                .andExpect(jsonPath("$.sources[?(@.name=='Remotive')].lastSyncAt", contains("2026-10-02T12:00:00Z")))
                .andExpect(jsonPath("$.sources[?(@.name=='Remotive')].nextSyncAt", contains("2026-10-02T18:00:00Z")))
                .andExpect(jsonPath("$.lastSyncAt").value("2026-10-02T12:00:00Z"));
    }

    private static List<JobPosting> postings(String source, int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> new JobPosting(null, source, source + "-" + i, "Java Developer", "ACME", "Remote",
                        true, "java", "https://example.com/" + source + "/" + i, "Java y Spring",
                        T0.minus(Duration.ofHours(i + 1)), T0))
                .toList();
    }
}
