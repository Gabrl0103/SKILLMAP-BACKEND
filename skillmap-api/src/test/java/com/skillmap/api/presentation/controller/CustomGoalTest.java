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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Objetivos propios (/api/goals/custom): crear, listar, editar, borrar y elegirlos con PUT /api/auth/me/goal; reglas y
 * códigos (400, 403, 404, 409); un usuario no ve ni toca los de otro; borrar el elegido vuelve al base anterior sin
 * tocar el progreso; todo lo que funciona con un objetivo base funciona con uno propio; al borrar la cuenta (o su
 * fila) se van en cascada.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-custom-goal-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-custom-goal-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "jobs.remotive.base-url=http://127.0.0.1:9",
        "jobs.arbeitnow.base-url=http://127.0.0.1:9",
        "skillmap.ai.remote-url=http://127.0.0.1:9",
        "skillmap.sync.remotive.enabled=false",
        "skillmap.sync.arbeitnow.enabled=false",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
class CustomGoalTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockBean
    JobSkillExtractor extractor;

    private String alice;
    private String bob;
    private List<Integer> skills;
    private List<Map<String, Object>> baseGoals;

    @BeforeEach
    void setUp() throws Exception {
        alice = TestAuth.register(mvc);
        bob = TestAuth.register(mvc);
        skills = JsonPath.read(body(mvc.perform(get("/api/skills").with(TestAuth.bearer(alice)))), "$[*].id");
        baseGoals = JsonPath.read(body(mvc.perform(get("/api/goals").with(TestAuth.bearer(alice)))), "$");
        assertThat(skills).hasSizeGreaterThanOrEqualTo(8);
        assertThat(baseGoals).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void createListAndChooseCustomGoal() throws Exception {
        String json = body(create(alice, "  Ingeniero   de Datos ", "ETL y pipelines", skills(0, 1, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Ingeniero de Datos"))
                .andExpect(jsonPath("$.description").value("ETL y pipelines"))
                .andExpect(jsonPath("$.custom").value(true))
                .andExpect(jsonPath("$.skills", hasSize(3)))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists()));
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        assertThat(id).isGreaterThanOrEqualTo(1_000_000L); // nunca choca con un objetivo base

        // GET /api/goals: primero los base (custom false) y después los propios
        mvc.perform(get("/api/goals").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$", hasSize(baseGoals.size() + 1)))
                .andExpect(jsonPath("$[0].custom").value(false))
                .andExpect(jsonPath("$[" + baseGoals.size() + "].id").value(id));
        mvc.perform(get("/api/goals/custom").with(TestAuth.bearer(alice))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/goals/" + id).with(TestAuth.bearer(alice))).andExpect(status().isOk());

        putGoal(alice, id).andExpect(status().isOk()).andExpect(jsonPath("$.goalId").value(id));
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice))).andExpect(jsonPath("$.goalId").value(id));
    }

    @Test
    void readinessHistoryAndOffersWorkWithACustomGoal() throws Exception {
        long id = createdId(alice, "Rol propio", skills(0, 1, 2, 3));
        putGoal(alice, id).andExpect(status().isOk());
        mvc.perform(get("/api/goals/" + id + "/readiness").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readinessPercentage").value(0))
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.gaps", hasSize(4)));

        setStatus(alice, skills.get(0), "MASTERED");
        mvc.perform(get("/api/goals/" + id + "/readiness").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.readinessPercentage").value(25)) // misma importancia: 1 de 4
                .andExpect(jsonPath("$.masteredCount").value(1))
                .andExpect(jsonPath("$.gaps", hasSize(3)));
        mvc.perform(get("/api/goals/" + id + "/readiness/history").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[-1:].percentage").value(25));
        mvc.perform(get("/api/jobs/recommended").param("goalId", String.valueOf(id)).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/jobs/recommended").with(TestAuth.bearer(alice))).andExpect(status().isOk());
    }

    @Test
    void usersCannotSeeOrTouchOtherUsersGoals() throws Exception {
        long id = createdId(alice, "Rol de Alice", skills(0, 1, 2));
        mvc.perform(get("/api/goals").with(TestAuth.bearer(bob))).andExpect(jsonPath("$", hasSize(baseGoals.size())));
        mvc.perform(get("/api/goals/custom").with(TestAuth.bearer(bob))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/goals/" + id).with(TestAuth.bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/goals/" + id + "/readiness").with(TestAuth.bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/goals/" + id + "/readiness/history").with(TestAuth.bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs/recommended").param("goalId", String.valueOf(id)).with(TestAuth.bearer(bob)))
                .andExpect(status().isNotFound());
        putGoal(bob, id).andExpect(status().isNotFound());
        update(bob, id, "Robado", null, skills(0, 1, 2)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/goals/custom/" + id).with(TestAuth.bearer(bob))).andExpect(status().isNotFound());
        // Bob puede usar el mismo nombre: los nombres son únicos por usuario
        create(bob, "Rol de Alice", null, skills(0, 1, 2)).andExpect(status().isCreated());
        mvc.perform(get("/api/goals/" + id).with(TestAuth.bearer(alice))).andExpect(status().isOk());
        mvc.perform(get("/api/goals/custom")).andExpect(status().isUnauthorized());
    }

    @Test
    void baseGoalsCannotBeEditedOrDeleted() throws Exception {
        long base = baseId(0);
        update(alice, base, "Mío", null, skills(0, 1, 2)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Los objetivos base no se pueden editar ni borrar."));
        mvc.perform(delete("/api/goals/custom/" + base).with(TestAuth.bearer(alice))).andExpect(status().isForbidden());
        update(alice, 999_999_999L, "Mío", null, skills(0, 1, 2)).andExpect(status().isNotFound());
    }

    @Test
    void editingKeepsSharedSkillsAndTheirNewOrder() throws Exception {
        long id = createdId(alice, "Rol editable", skills(0, 1, 2));
        // Habilidades que siguen (1 y 2), otra que entra (3) y otro orden: sin chocar con el índice (goal_id, skill_id)
        update(alice, id, "Rol editado", "Nueva", skills(2, 3, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rol editado"))
                .andExpect(jsonPath("$.skills[0].skillId").value(skills.get(2)))
                .andExpect(jsonPath("$.skills[1].skillId").value(skills.get(3)))
                .andExpect(jsonPath("$.skills[2].skillId").value(skills.get(1)));
        update(alice, id, "Rol editado", null, skills(2, 3, 1, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", hasSize(4)))
                .andExpect(jsonPath("$.description").doesNotExist());
        mvc.perform(get("/api/goals/custom").with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$[0].skills[3].skillId").value(skills.get(0)));
    }

    @Test
    void validationAndConflicts() throws Exception {
        String baseTitle = (String) baseGoals.get(0).get("title");
        create(alice, "  " + baseTitle.toUpperCase() + "  ", null, skills(0, 1, 2)).andExpect(status().isConflict());
        create(alice, "A", null, skills(0, 1, 2)).andExpect(status().isBadRequest());
        create(alice, "Rol", "d".repeat(121), skills(0, 1, 2)).andExpect(status().isBadRequest());
        create(alice, "Rol", null, skills(0, 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elige entre 3 y 8 habilidades."));
        create(alice, "Rol", null, skills(0, 1, 2, 3, 4, 5, 6, 7) + ",999").andExpect(status().isBadRequest());
        create(alice, "Rol", null, skills(0, 1, 2, 3, 4, 5, 6, 7, 8 % skills.size())).andExpect(status().isBadRequest());
        create(alice, "Rol", null, skills(0, 0, 1)).andExpect(status().isBadRequest());
        create(alice, "Rol", null, skills(0, 1) + ",999999").andExpect(status().isBadRequest());
        create(alice, "<i>Rol</i>", null, skills(0, 1, 2)).andExpect(status().isBadRequest());

        long first = createdId(alice, "Rol Uno", skills(0, 1, 2));
        create(alice, "rol   uno", null, skills(0, 1, 2)).andExpect(status().isConflict());
        createdId(alice, "Rol Dos", skills(0, 1, 2));
        createdId(alice, "Rol Tres", skills(0, 1, 2));
        create(alice, "Rol Cuatro", null, skills(0, 1, 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Puedes tener como máximo 3 objetivos propios. Borra uno para crear otro."));
        // Editar: puede quedarse con su nombre, no tomar el de otro propio
        update(alice, first, "ROL UNO", "Ahora con descripción", skills(3, 4, 5, 6)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("ROL UNO"))
                .andExpect(jsonPath("$.skills", hasSize(4)));
        update(alice, first, "Rol Dos", null, skills(0, 1, 2)).andExpect(status().isConflict());
        // Al borrar uno se puede volver a crear
        mvc.perform(delete("/api/goals/custom/" + first).with(TestAuth.bearer(alice))).andExpect(status().isOk());
        create(alice, "Rol Cuatro", null, skills(0, 1, 2)).andExpect(status().isCreated());
    }

    @Test
    void deletingTheChosenGoalGoesBackToThePreviousBaseGoalAndKeepsProgress() throws Exception {
        long base = baseId(1);
        putGoal(alice, base).andExpect(status().isOk());
        long custom = createdId(alice, "Rol temporal", skills(0, 1, 2));
        putGoal(alice, custom).andExpect(status().isOk());
        setStatus(alice, skills.get(0), "MASTERED");
        setStatus(alice, skills.get(1), "IN_PROGRESS");

        mvc.perform(delete("/api/goals/custom/" + custom).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(base));
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice))).andExpect(jsonPath("$.goalId").value(base));
        mvc.perform(get("/api/goals/" + custom).with(TestAuth.bearer(alice))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from readiness_snapshots where goal_id = ?", Long.class, custom))
                .isZero();
        mvc.perform(get("/api/skills/" + skills.get(0)).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.status").value("MASTERED"));
        mvc.perform(get("/api/skills/" + skills.get(1)).with(TestAuth.bearer(alice)))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Sin base elegido antes: al primero. Borrar uno que no es el elegido no cambia nada.
        long other = createdId(bob, "Otro rol", skills(0, 1, 2));
        long chosen = createdId(bob, "Rol elegido", skills(3, 4, 5));
        putGoal(bob, chosen).andExpect(status().isOk());
        mvc.perform(delete("/api/goals/custom/" + other).with(TestAuth.bearer(bob)))
                .andExpect(jsonPath("$.goalId").value(chosen));
        mvc.perform(delete("/api/goals/custom/" + chosen).with(TestAuth.bearer(bob)))
                .andExpect(jsonPath("$.goalId").value(baseId(0)));
    }

    @Test
    void customGoalsGoAwayWithTheAccount() throws Exception {
        String email = "borrar-" + UUID.randomUUID() + "@test.local";
        String token = TestAuth.register(mvc, email);
        createdId(token, "Rol a borrar", skills(0, 1, 2));
        Long userId = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        assertThat(count("custom_goals", userId)).isEqualTo(1);

        mvc.perform(delete("/api/auth/me").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + TestAuth.PASSWORD + "\"}")).andExpect(status().isNoContent());
        assertThat(count("custom_goals", userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from custom_goal_skills s where not exists "
                + "(select 1 from custom_goals g where g.id = s.goal_id)", Long.class)).isZero();
    }

    @Test
    void theDatabaseCascadesWhenTheUserRowIsDeleted() throws Exception {
        String email = "cascada-" + UUID.randomUUID() + "@test.local";
        String token = TestAuth.register(mvc, email);
        long id = createdId(token, "Rol en cascada", skills(0, 1, 2));
        Long userId = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        jdbc.update("delete from sessions where user_id = ?", userId);
        jdbc.update("delete from users where id = ?", userId); // sin pasar por la app: la clave foránea borra lo demás
        assertThat(count("custom_goals", userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from custom_goal_skills where goal_id = ?", Long.class, id))
                .isZero();
    }

    // --- Ayudas -------------------------------------------------------------------------------

    private long count(String table, Long userId) {
        return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId);
    }

    private long baseId(int index) {
        return ((Number) baseGoals.get(index).get("id")).longValue();
    }

    /** Ids de habilidades del catálogo por posición, separados por comas (para el JSON). */
    private String skills(int... positions) {
        StringBuilder out = new StringBuilder();
        for (int p : positions) {
            if (!out.isEmpty()) out.append(',');
            out.append(skills.get(p));
        }
        return out.toString();
    }

    private ResultActions create(String token, String name, String description, String skillIds) throws Exception {
        return mvc.perform(post("/api/goals/custom").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(name, description, skillIds)));
    }

    private long createdId(String token, String name, String skillIds) throws Exception {
        String json = body(create(token, name, null, skillIds).andExpect(status().isCreated()));
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions update(String token, long id, String name, String description, String skillIds)
            throws Exception {
        return mvc.perform(put("/api/goals/custom/" + id).with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(name, description, skillIds)));
    }

    private ResultActions putGoal(String token, long goalId) throws Exception {
        return mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + goalId + "}"));
    }

    private void setStatus(String token, int skillId, String status) throws Exception {
        mvc.perform(put("/api/skills/" + skillId + "/status").with(TestAuth.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private static String json(String name, String description, String skillIds) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"")
                + ",\"description\":" + (description == null ? "null" : "\"" + description + "\"")
                + ",\"skillIds\":[" + skillIds + "]}";
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
