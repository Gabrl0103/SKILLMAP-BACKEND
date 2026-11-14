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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Habilidades propias de un objetivo (newSkills en /api/goals/custom) y sugerencias por rol
 * (/api/goals/custom/suggestions): se crean, se listan solo para su dueño sin datos de demanda, cambian de estado,
 * cuentan en la ruta (al final de las brechas), no se buscan en las ofertas, y se borran con el objetivo, al quitarlas
 * de él o con la cuenta.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-custom-skills-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-custom-skills-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "jobs.remotive.base-url=http://127.0.0.1:9",
        "jobs.arbeitnow.base-url=http://127.0.0.1:9",
        "skillmap.ai.remote-url=http://127.0.0.1:9",
        "skillmap.sync.remotive.enabled=false",
        "skillmap.sync.arbeitnow.enabled=false",
        "skillmap.goals.suggest-ai-timeout=2s",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
class CustomSkillsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockBean
    JobSkillExtractor extractor;

    private String alice;
    private String bob;
    private List<Integer> catalog;

    @BeforeEach
    void setUp() throws Exception {
        alice = TestAuth.register(mvc);
        bob = TestAuth.register(mvc);
        catalog = JsonPath.read(body(mvc.perform(get("/api/skills").with(TestAuth.bearer(alice)))), "$[*].id");
    }

    @Test
    void chefWithOnlyOwnSkillsWorksEverywhereWithoutMarketData() throws Exception {
        String json = body(save(post("/api/goals/custom"), alice,
                "{\"name\":\"Chef\",\"skillIds\":[],\"newSkills\":[\"Cocina francesa\",\" Panadería \",\"Gestión de cocina\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills", hasSize(3))));
        long goalId = id(json);
        List<Integer> skillIds = JsonPath.read(json, "$.skills[*].skillId");

        // GET /api/skills: el catálogo y, al final, las propias con categoría "Personalizada" y sin demanda
        String all = body(mvc.perform(get("/api/skills").with(TestAuth.bearer(alice))).andExpect(status().isOk()));
        List<Map<String, Object>> own = JsonPath.read(all, "$[?(@.custom == true)]");
        assertThat(own).extracting(s -> s.get("name")).containsExactly("Cocina francesa", "Panadería", "Gestión de cocina");
        assertThat(own).allSatisfy(s -> {
            assertThat(s.get("category")).isEqualTo("Personalizada");
            assertThat(s.get("demandSource")).isEqualTo("NONE");
            assertThat(s.get("demandPercentage")).isEqualTo(0);
            assertThat(((Number) s.get("goalId")).longValue()).isEqualTo(goalId);
        });
        List<Boolean> catalogFlags = JsonPath.read(all, "$[?(@.custom == false)].custom");
        assertThat(catalogFlags).hasSize(catalog.size());

        // Estado, ruta y % igual que cualquier habilidad
        mvc.perform(put("/api/skills/" + skillIds.get(1) + "/status").with(TestAuth.bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MASTERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custom").value(true));
        mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + goalId + "}")).andExpect(status().isOk());
        mvc.perform(get("/api/goals/" + goalId + "/readiness").with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readinessPercentage").value(33))
                .andExpect(jsonPath("$.masteredCount").value(1))
                .andExpect(jsonPath("$.gaps[*].name").value(org.hamcrest.Matchers.contains("Cocina francesa", "Gestión de cocina")))
                .andExpect(jsonPath("$.gaps[0].demandSource").value("NONE"));

        // Ofertas: sin habilidades del catálogo no hay ofertas comparables (lista vacía, no error)
        mvc.perform(get("/api/jobs/recommended").param("goalId", String.valueOf(goalId)).with(TestAuth.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Bob no las ve ni las puede tocar
        String bobSkills = body(mvc.perform(get("/api/skills").with(TestAuth.bearer(bob))));
        assertThat((List<Object>) JsonPath.read(bobSkills, "$[?(@.custom == true)]")).isEmpty();
        mvc.perform(get("/api/skills/" + skillIds.get(0)).with(TestAuth.bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(put("/api/skills/" + skillIds.get(0) + "/status").with(TestAuth.bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MASTERED\"}"))
                .andExpect(status().isNotFound());
        save(post("/api/goals/custom"), bob, "{\"name\":\"Copia\",\"skillIds\":[" + skillIds.get(0) + ","
                + catalog.get(0) + "," + catalog.get(1) + "]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void mixedGoalSortsOwnSkillsLastAndKeepsCatalogOffers() throws Exception {
        long goalId = id(body(save(post("/api/goals/custom"), alice, "{\"name\":\"Diseñador UX\",\"skillIds\":["
                + catalog.get(0) + "," + catalog.get(1) + "],\"newSkills\":[\"Figma\",\"Prototipado\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills", hasSize(4)))));
        String readiness = body(mvc.perform(get("/api/goals/" + goalId + "/readiness").with(TestAuth.bearer(alice))));
        List<Boolean> customFlags = JsonPath.read(readiness, "$.gaps[*].demandSource");
        assertThat(customFlags).hasSize(4);
        List<String> names = JsonPath.read(readiness, "$.gaps[*].name");
        assertThat(names.subList(2, 4)).containsExactly("Figma", "Prototipado");
    }

    @Test
    void editingRemovesDroppedOwnSkillsWithTheirProgressAndAddsNewOnes() throws Exception {
        String json = body(save(post("/api/goals/custom"), alice,
                "{\"name\":\"Chef\",\"skillIds\":[],\"newSkills\":[\"Cocina francesa\",\"Panadería\",\"Repostería\"]}"));
        long goalId = id(json);
        List<Integer> ids = JsonPath.read(json, "$.skills[*].skillId");
        mvc.perform(put("/api/skills/" + ids.get(2) + "/status").with(TestAuth.bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());

        // Conserva las dos primeras, quita Repostería y agrega Emplatado y una del catálogo
        String edited = body(save(put("/api/goals/custom/" + goalId), alice, "{\"name\":\"Chef\",\"skillIds\":["
                + ids.get(0) + "," + ids.get(1) + "," + catalog.get(0) + "],\"newSkills\":[\"Emplatado\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", hasSize(4))));
        List<Integer> after = JsonPath.read(edited, "$.skills[*].skillId");
        assertThat(after).startsWith(ids.get(0), ids.get(1), catalog.get(0)).doesNotContain(ids.get(2));
        assertThat(count("select count(*) from skills where id = ?", ids.get(2))).isZero();
        assertThat(count("select count(*) from user_skills where skill_id = ?", ids.get(2))).isZero();

        // Repetir una del catálogo por nombre: mejor elegirla de la lista
        save(put("/api/goals/custom/" + goalId), alice, "{\"name\":\"Chef\",\"skillIds\":[" + ids.get(0) + ","
                + ids.get(1) + "],\"newSkills\":[\"python\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Esa habilidad ya está en el catálogo: elígela de la lista."));
    }

    @Test
    void deletingTheGoalOrTheAccountDeletesItsOwnSkills() throws Exception {
        long goalId = id(body(save(post("/api/goals/custom"), alice,
                "{\"name\":\"Chef\",\"skillIds\":[],\"newSkills\":[\"Cocina francesa\",\"Panadería\",\"Repostería\"]}")));
        long userId = ((Number) JsonPath.read(body(mvc.perform(get("/api/auth/me").with(TestAuth.bearer(alice)))),
                "$.id")).longValue();
        assertThat(count("select count(*) from skills where owner_id = ?", userId)).isEqualTo(3);

        mvc.perform(delete("/api/goals/custom/" + goalId).with(TestAuth.bearer(alice))).andExpect(status().isOk());
        assertThat(count("select count(*) from skills where owner_id = ?", userId)).isZero();

        save(post("/api/goals/custom"), alice,
                "{\"name\":\"Chef 2\",\"skillIds\":[],\"newSkills\":[\"Cocina\",\"Pan\",\"Postres\"]}");
        mvc.perform(delete("/api/auth/me").with(TestAuth.bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + TestAuth.PASSWORD + "\"}")).andExpect(status().isNoContent());
        assertThat(count("select count(*) from skills where owner_id = ?", userId)).isZero();
        // El catálogo sigue intacto
        assertThat(count("select count(*) from skills where owner_id is null", null)).isEqualTo(catalog.size());
    }

    @Test
    void suggestionsComeFromTheDictionaryWithoutAi() throws Exception {
        mvc.perform(suggest(alice, "Diseñador UX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("dictionary"))
                .andExpect(jsonPath("$.skills.length()").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(5), org.hamcrest.Matchers.lessThanOrEqualTo(8))))
                .andExpect(jsonPath("$.skills[0].name").value("Figma"))
                .andExpect(jsonPath("$.skills[0].skillId").doesNotExist())
                .andExpect(jsonPath("$.skills[0].custom").value(true));
        mvc.perform(suggest(alice, "Backend Python"))
                .andExpect(jsonPath("$.skills[0].name").value("Python"))
                .andExpect(jsonPath("$.skills[0].skillId").isNumber())
                .andExpect(jsonPath("$.skills[0].custom").value(false));
        verify(extractor, never()).extractSkills(anyString(), any());
    }

    @Test
    void unknownRolesUseTheAiAsFallbackOrEndWithNothing() throws Exception {
        when(extractor.extractSkills(anyString(), any())).thenReturn(List.of("Cocina francesa", "Higiene alimentaria"));
        mvc.perform(suggest(alice, "Chef"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ai"))
                .andExpect(jsonPath("$.skills[*].name").value(org.hamcrest.Matchers.contains("Cocina francesa", "Higiene alimentaria")));

        when(extractor.extractSkills(anyString(), any())).thenThrow(new IllegalStateException("sin red"));
        mvc.perform(suggest(alice, "Chef"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("none"))
                .andExpect(jsonPath("$.skills", hasSize(0)));

        mvc.perform(suggest(alice, "x")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/goals/custom/suggestions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Chef\"}")).andExpect(status().isUnauthorized());
    }

    private ResultActions save(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String token, String json) throws Exception {
        return mvc.perform(request.with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder suggest(String token,
                                                                                                      String role) {
        return post("/api/goals/custom/suggestions").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}");
    }

    private int count(String sql, Object arg) {
        Integer n = arg == null ? jdbc.queryForObject(sql, Integer.class) : jdbc.queryForObject(sql, Integer.class, arg);
        return n == null ? 0 : n;
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
