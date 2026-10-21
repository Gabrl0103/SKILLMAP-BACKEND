package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.JobPostingRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.infrastructure.persistence.JobPostingJpaRepository;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/jobs/recommended y GET /api/jobs/{id}. Las habilidades de cada oferta salen de SkillMatcher: la IA
 * está simulada y nunca se usa, las fuentes de ofertas apuntan a un puerto cerrado y la base es en memoria.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-job-recommendation-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-job-recommendation-test",
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
class JobRecommendationTest {

    private static final String RECOMMENDED = "/api/jobs/recommended";
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JobPostingRepository postings;

    @Autowired
    JobPostingJpaRepository postingsTable;

    @Autowired
    SkillRepository skillRepository;

    @MockBean
    JobSkillExtractor extractor;

    /** Marca única dentro de cada descripción: no debe salir en /recommended ni en los logs. */
    private final String marker = "MARCADOR-" + UUID.randomUUID();
    private String alice;
    private String bob;
    private Map<String, Integer> ids;

    @BeforeEach
    void setUp() throws Exception {
        postingsTable.deleteAll(); // fuera las ofertas del seed: cada test usa las suyas
        // Backend Java = Java, Spring Boot, SQL, Docker, Testing; Data Scientist = Python, Machine Learning, SQL, Pandas, Estadística
        ids = save(
                posting("A", "Backend Engineer", "Java, Spring Boot and PostgreSQL.", 5),  // Java, Spring Boot, SQL
                posting("B", "Fullstack Developer", "Java and React.", 1),                   // Java, React
                posting("C", "Data Analyst", "Python and Pandas.", 3),                       // Python, Pandas
                posting("D", "Junior Developer", "Java only.", 1),                           // Java: una sola habilidad
                posting("E", "Platform Engineer", "Docker, AWS and Kubernetes.", 2));         // Docker, AWS, Kubernetes
        alice = TestAuth.register(mvc);
        bob = TestAuth.register(mvc);
    }

    @AfterEach
    void aiIsNeverUsed() {
        verifyNoInteractions(extractor);
    }

    // ---------- Orden, ponderación y relevancia ----------

    @Test
    void relevantPostingsOnlyOrderedByMatchThenGoalSkillsThenNewest() throws Exception {
        setGoal(alice, "Backend Java");

        // Todo pendiente: 0 % en todas; A tiene 3 habilidades del objetivo; B y E una, B es más reciente.
        // C (sin habilidades del objetivo) y D (una sola habilidad) no son relevantes.
        recommended(alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(ids.get("A"), ids.get("B"), ids.get("E"))))
                .andExpect(jsonPath("$[*].matchPercentage", contains(0, 0, 0)));
    }

    @Test
    void changingASkillStatusChangesPercentagesAndOrderWithInProgressAtHalf() throws Exception {
        setGoal(alice, "Backend Java");

        setStatus(alice, "Java", "MASTERED");
        // B: 1/2 = 50; A: 1/3 = 33; E: 0
        recommended(alice)
                .andExpect(jsonPath("$[*].id", contains(ids.get("B"), ids.get("A"), ids.get("E"))))
                .andExpect(jsonPath("$[*].matchPercentage", contains(50, 33, 0)));

        setStatus(alice, "Spring Boot", "IN_PROGRESS");
        // A: (1 + 0,5)/3 = 50, empata con B y gana por tener más habilidades del objetivo
        recommended(alice)
                .andExpect(jsonPath("$[*].id", contains(ids.get("A"), ids.get("B"), ids.get("E"))))
                .andExpect(jsonPath("$[*].matchPercentage", contains(50, 50, 0)))
                .andExpect(jsonPath("$[0].masteredSkills", contains("Java")))
                .andExpect(jsonPath("$[0].missingSkills[*].name", contains("Spring Boot", "SQL")))
                .andExpect(jsonPath("$[0].missingSkills[*].status", contains("IN_PROGRESS", "PENDING")));
    }

    @Test
    void changingGoalChangesTheRecommendations() throws Exception {
        setGoal(alice, "Backend Java");
        recommended(alice).andExpect(jsonPath("$[*].id", contains(ids.get("A"), ids.get("B"), ids.get("E"))));

        // Data Scientist: C tiene 2 habilidades del objetivo (Python, Pandas) y A una (SQL)
        setGoal(alice, "Data Scientist");
        recommended(alice).andExpect(jsonPath("$[*].id", contains(ids.get("C"), ids.get("A"))));
    }

    @Test
    void withoutASavedGoalUsesTheFirstOne() throws Exception {
        String goals = mvc.perform(get("/api/goals").with(TestAuth.bearer(bob))).andReturn().getResponse().getContentAsString();
        String firstTitle = JsonPath.read(goals, "$[0].title");
        String withoutGoal = recommended(alice).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        setGoal(bob, firstTitle);
        String withFirstGoal = recommended(bob).andReturn().getResponse().getContentAsString();

        assertThat(withoutGoal).isEqualTo(withFirstGoal);
    }

    @Test
    void goalIdParameterOverridesTheSavedGoalAndUnknownIs404() throws Exception {
        setGoal(alice, "Backend Java");
        long dataScientist = goalId(alice, "Data Scientist");

        mvc.perform(get(RECOMMENDED + "?goalId=" + dataScientist).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(ids.get("C"), ids.get("A"))));
        mvc.perform(get(RECOMMENDED + "?goalId=999999").with(TestAuth.bearer(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el objetivo con id 999999"));
        mvc.perform(get(RECOMMENDED + "?goalId=abc").with(TestAuth.bearer(alice)))
                .andExpect(status().isBadRequest());
        // El objetivo guardado no cambia
        recommended(alice).andExpect(jsonPath("$[*].id", contains(ids.get("A"), ids.get("B"), ids.get("E"))));
    }

    @Test
    void progressIsPerUser() throws Exception {
        setGoal(alice, "Backend Java");
        setGoal(bob, "Backend Java");
        setStatus(alice, "Java", "MASTERED");

        recommended(alice).andExpect(jsonPath("$[0].matchPercentage").value(50));
        recommended(bob)
                .andExpect(jsonPath("$[*].matchPercentage", contains(0, 0, 0)))
                .andExpect(jsonPath("$[0].masteredSkills").isEmpty());
    }

    // ---------- Contenido de la respuesta ----------

    @Test
    void eachItemHasItsDataButNeverTheFullText(CapturedOutput output) throws Exception {
        setGoal(alice, "Backend Java");

        String body = recommended(alice)
                .andExpect(jsonPath("$[0].id").value(ids.get("A")))
                .andExpect(jsonPath("$[0].title").value("Backend Engineer"))
                .andExpect(jsonPath("$[0].company").value("ACME A"))
                .andExpect(jsonPath("$[0].source").value("Arbeitnow"))
                .andExpect(jsonPath("$[0].url").value("https://example.com/jobs/A"))
                .andExpect(jsonPath("$[0].publishedAt").value("2026-10-15T12:00:00Z"))
                .andExpect(jsonPath("$[0].matchPercentage").value(0))
                .andExpect(jsonPath("$[0].masteredSkills").isEmpty())
                .andExpect(jsonPath("$[0].missingSkills", hasSize(3)))
                .andExpect(jsonPath("$[0].description").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(marker);
        assertThat(output.getAll()).doesNotContain(marker);
    }

    @Test
    void jobDetailHasTheFullTextFor404AndRequiresSession(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/jobs/" + ids.get("A")).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ids.get("A")))
                .andExpect(jsonPath("$.title").value("Backend Engineer"))
                .andExpect(jsonPath("$.source").value("Arbeitnow"))
                .andExpect(jsonPath("$.url").value("https://example.com/jobs/A"))
                .andExpect(jsonPath("$.description").value("Java, Spring Boot and PostgreSQL. " + marker));

        mvc.perform(get("/api/jobs/999999").with(TestAuth.bearer(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró la oferta."));
        mvc.perform(get("/api/jobs/abc").with(TestAuth.bearer(alice)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/jobs/" + ids.get("A"))).andExpect(status().isUnauthorized());

        assertThat(output.getAll()).doesNotContain(marker);
    }

    // ---------- Límite y sesión ----------

    @Test
    void limitDefaultsToTenAndAllowsUpToThirty() throws Exception {
        save(IntStream.range(0, 35)
                .mapToObj(i -> posting("X" + i, "Java Developer " + i, "Java and SQL.", 10 + i))
                .toArray(JobPosting[]::new));
        setGoal(alice, "Backend Java");

        recommended(alice).andExpect(jsonPath("$", hasSize(10)));
        mvc.perform(get(RECOMMENDED + "?limit=1").with(TestAuth.bearer(alice))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get(RECOMMENDED + "?limit=30").with(TestAuth.bearer(alice))).andExpect(jsonPath("$", hasSize(30)));
    }

    @Test
    void invalidLimitIs400InSpanish() throws Exception {
        for (String limit : List.of("0", "31", "-1")) {
            mvc.perform(get(RECOMMENDED + "?limit=" + limit).with(TestAuth.bearer(alice)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("El parámetro 'limit' debe estar entre 1 y 30."));
        }
        mvc.perform(get(RECOMMENDED + "?limit=abc").with(TestAuth.bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'limit' no tiene un valor válido."));
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(get(RECOMMENDED)).andExpect(status().isUnauthorized());
        mvc.perform(get(RECOMMENDED).header("Authorization", "Bearer token-falso")).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutPostingsTheListIsEmpty() throws Exception {
        postingsTable.deleteAll();
        recommended(alice).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    // ---------- Ayudas ----------

    private ResultActions recommended(String token) throws Exception {
        return mvc.perform(get(RECOMMENDED).with(TestAuth.bearer(token)));
    }

    private JobPosting posting(String key, String title, String text, int daysAgo) {
        return new JobPosting(null, "Arbeitnow", "rec-" + key + "-" + UUID.randomUUID(), title, "ACME " + key, "Remote", true,
                "", "https://example.com/jobs/" + key, text + " " + marker, NOW.minus(Duration.ofDays(daysAgo)), NOW);
    }

    /** Guarda las ofertas y devuelve su id por clave (la que va al final de la URL). */
    private Map<String, Integer> save(JobPosting... toSave) {
        postings.saveAll(List.of(toSave));
        return postings.findAll().stream().collect(Collectors.toMap(
                p -> p.url().substring(p.url().lastIndexOf('/') + 1), p -> p.id().intValue(), (a, b) -> a));
    }

    private void setGoal(String token, String title) throws Exception {
        mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goalId(token, title) + "}"))
                .andExpect(status().isOk());
    }

    private long goalId(String token, String title) throws Exception {
        String goals = mvc.perform(get("/api/goals").with(TestAuth.bearer(token))).andReturn().getResponse().getContentAsString();
        List<Integer> goalIds = new ArrayList<>(JsonPath.read(goals, "$[?(@.title == '" + title + "')].id"));
        assertThat(goalIds).hasSize(1);
        return goalIds.get(0);
    }

    private void setStatus(String token, String skillName, String status) throws Exception {
        long skillId = skillRepository.findAll().stream()
                .filter(s -> s.getName().equals(skillName)).findFirst().orElseThrow().getId();
        mvc.perform(put("/api/skills/" + skillId + "/status").with(TestAuth.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }
}
