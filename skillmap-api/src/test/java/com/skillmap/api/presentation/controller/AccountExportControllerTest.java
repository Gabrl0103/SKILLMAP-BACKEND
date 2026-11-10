package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/auth/me/export ("Exportar mis datos"): exige sesión, trae perfil, objetivo, estados de habilidades,
 * enlaces, proyectos e historial de preparación de la cuenta, nunca la contraseña ni el token, y nada de otra cuenta.
 * Base en memoria y carpeta temporal; nunca se llama a una API externa.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-export-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-export-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
class AccountExportControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void exportRequiresSession() throws Exception {
        mvc.perform(get("/api/auth/me/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportBringsTheAccountDataWithoutSecrets() throws Exception {
        String email = "export-" + UUID.randomUUID() + "@test.local";
        String token = TestAuth.register(mvc, email);
        mvc.perform(patch("/api/auth/me").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana López\",\"headline\":\"Desarrolladora Frontend\"}")).andExpect(status().isOk());
        String goals = mvc.perform(get("/api/goals").with(TestAuth.bearer(token))).andReturn().getResponse().getContentAsString();
        Integer goalId = JsonPath.read(goals, "$[0].id");
        String goalTitle = JsonPath.read(goals, "$[0].title");
        mvc.perform(put("/api/auth/me/goal").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + goalId + "}")).andExpect(status().isOk());
        String skills = mvc.perform(get("/api/skills").with(TestAuth.bearer(token))).andReturn().getResponse().getContentAsString();
        Integer skillId = JsonPath.read(skills, "$[0].id");
        String skillName = JsonPath.read(skills, "$[0].name");
        mvc.perform(put("/api/skills/" + skillId + "/status").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"MASTERED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/profile/links").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"GitHub\",\"url\":\"https://github.com/ana\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/profile/projects").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Portafolio\",\"description\":\"Mi sitio\",\"url\":\"https://ana.dev\",\"tags\":[\"React\"]}"))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/auth/me/export").with(TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.format").value("cenit-export"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.account.name").value("Ana López"))
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.account.headline").value("Desarrolladora Frontend"))
                .andExpect(jsonPath("$.goal.title").value(goalTitle))
                .andExpect(jsonPath("$.skills[?(@.name == '" + skillName + "')].status").value(hasItem("MASTERED")))
                .andExpect(jsonPath("$.links", hasSize(1)))
                .andExpect(jsonPath("$.links[0].url").value("https://github.com/ana"))
                .andExpect(jsonPath("$.projects[0].name").value("Portafolio"))
                .andExpect(jsonPath("$.projects[0].tags[0]").value("React"))
                .andExpect(jsonPath("$.analyses", hasSize(0)))
                .andExpect(jsonPath("$.readinessHistory").isArray())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(TestAuth.PASSWORD).doesNotContain(token)
                .doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("hash").doesNotContain("$2a$");
        List<Object> history = JsonPath.read(body, "$.readinessHistory[*].goal");
        assertThat(history).isNotEmpty().allMatch(goalTitle::equals);
    }

    @Test
    void exportOnlyBringsTheSessionAccount() throws Exception {
        String mine = TestAuth.register(mvc);
        String other = TestAuth.register(mvc);
        mvc.perform(post("/api/profile/links").with(TestAuth.bearer(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Ajeno\",\"url\":\"https://example.com/ajeno\"}")).andExpect(status().isCreated());

        String body = mvc.perform(get("/api/auth/me/export").with(TestAuth.bearer(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links", hasSize(0)))
                .andExpect(jsonPath("$.goal").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("example.com/ajeno");
    }
}
