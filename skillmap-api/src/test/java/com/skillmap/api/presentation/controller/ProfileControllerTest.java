package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.port.TokenGenerator;
import com.skillmap.api.domain.repository.SessionRepository;
import com.skillmap.api.infrastructure.persistence.ProfileLinkJpaRepository;
import com.skillmap.api.infrastructure.persistence.ProfileProjectJpaRepository;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enlaces y proyectos del perfil: /api/profile/links y /api/profile/projects. Base en memoria y
 * carpeta temporal; nunca se llama a una API externa.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-profile-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-profile-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY="
})
@AutoConfigureMockMvc
class ProfileControllerTest {

    private static final String LINKS = "/api/profile/links";
    private static final String PROJECTS = "/api/profile/projects";
    private static final String INVALID_URL =
            "La URL debe empezar por http:// o https:// (por ejemplo, https://mi-portfolio.com).";

    @Autowired
    MockMvc mvc;

    @Autowired
    ProfileLinkJpaRepository linksTable;

    @Autowired
    ProfileProjectJpaRepository projectsTable;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionRepository sessions;

    @Autowired
    TokenGenerator tokens;

    // ---------- Enlaces ----------

    @Test
    void linksCrud() throws Exception {
        String token = TestAuth.register(mvc);
        mvc.perform(get(LINKS).with(TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        Long id = idOf(mvc.perform(send(post(LINKS), token, link("  GitHub  ", "  https://github.com/ana  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("GitHub"))
                .andExpect(jsonPath("$.url").value("https://github.com/ana"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(send(post(LINKS), token, link("Portfolio", "http://ana.dev")))
                .andExpect(status().isCreated());

        mvc.perform(send(put(LINKS + "/" + id), token, link("GitHub personal", "HTTPS://github.com/ana-lopez")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.label").value("GitHub personal"))
                .andExpect(jsonPath("$.url").value("HTTPS://github.com/ana-lopez"));
        mvc.perform(get(LINKS).with(TestAuth.bearer(token)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].label").value("GitHub personal"))
                .andExpect(jsonPath("$[1].label").value("Portfolio"));

        mvc.perform(delete(LINKS + "/" + id).with(TestAuth.bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get(LINKS).with(TestAuth.bearer(token)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("Portfolio"));
        mvc.perform(delete(LINKS + "/" + id).with(TestAuth.bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el enlace."));
    }

    @Test
    void theNinthLinkIsRejected() throws Exception {
        String token = TestAuth.register(mvc);
        for (int i = 1; i <= 8; i++) {
            mvc.perform(send(post(LINKS), token, link("Enlace " + i, "https://example.com/" + i)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(send(post(LINKS), token, link("Enlace 9", "https://example.com/9")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Puedes tener como máximo 8 enlaces. Borra uno para agregar otro."));
        mvc.perform(get(LINKS).with(TestAuth.bearer(token))).andExpect(jsonPath("$", hasSize(8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", " JavaScript:alert(1)", "data:text/html,<script>alert(1)</script>",
            "file:///C:/Windows/win.ini", "ftp://example.com", "vbscript:msgbox(1)", "example.com", "https:example.com",
            "https://", "http://exa mple.com", "//example.com"})
    void onlyHttpAndHttpsUrlsAreAccepted(String url) throws Exception {
        String token = TestAuth.register(mvc);
        mvc.perform(send(post(LINKS), token, link("Mi web", url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_URL));
        mvc.perform(send(post(PROJECTS), token, project("Proyecto", null, url, List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_URL));
        mvc.perform(get(LINKS).with(TestAuth.bearer(token))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(PROJECTS).with(TestAuth.bearer(token))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void invalidLinksReturn400() throws Exception {
        String token = TestAuth.register(mvc);
        mvc.perform(send(post(LINKS), token, link("   ", "https://example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La etiqueta del enlace debe tener entre 1 y 40 caracteres."));
        mvc.perform(send(post(LINKS), token, link("x".repeat(41), "https://example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La etiqueta del enlace debe tener entre 1 y 40 caracteres."));
        mvc.perform(send(post(LINKS), token, link("Mi web", "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Escribe la URL del enlace."));
        mvc.perform(send(post(LINKS), token, link("Mi web", "https://example.com/" + "a".repeat(2048))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La URL puede tener como máximo 2048 caracteres."));
        // 40 emojis: 40 caracteres aunque ocupen 80 unidades UTF-16.
        mvc.perform(send(post(LINKS), token, link("🚀".repeat(40), "https://example.com")))
                .andExpect(status().isCreated());
    }

    // ---------- Proyectos ----------

    @Test
    void projectsCrud() throws Exception {
        String token = TestAuth.register(mvc);
        Long id = idOf(mvc.perform(send(post(PROJECTS), token,
                        project("  SkillMap  ", "  GPS de carrera  ", "https://github.com/ana/skillmap",
                                List.of(" Java ", "Spring", "", "Java"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("SkillMap"))
                .andExpect(jsonPath("$.description").value("GPS de carrera"))
                .andExpect(jsonPath("$.url").value("https://github.com/ana/skillmap"))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.tags[0]").value("Java"))
                .andExpect(jsonPath("$.tags[1]").value("Spring"))
                .andReturn().getResponse().getContentAsString());

        // URL y descripción son opcionales; PUT reemplaza también las etiquetas.
        mvc.perform(send(put(PROJECTS + "/" + id), token, project("SkillMap 2", "", null, List.of("JS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SkillMap 2"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.url").isEmpty())
                .andExpect(jsonPath("$.tags", hasSize(1)))
                .andExpect(jsonPath("$.tags[0]").value("JS"));
        mvc.perform(get(PROJECTS).with(TestAuth.bearer(token)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("SkillMap 2"))
                .andExpect(jsonPath("$[0].tags[0]").value("JS"));

        mvc.perform(delete(PROJECTS + "/" + id).with(TestAuth.bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get(PROJECTS).with(TestAuth.bearer(token))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(send(put(PROJECTS + "/" + id), token, project("Otro", null, null, List.of())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el proyecto."));
        assertThat(jdbc.queryForObject("select count(*) from profile_project_tags where project_id = ?",
                Long.class, id)).isZero();
    }

    @Test
    void theEleventhProjectIsRejected() throws Exception {
        String token = TestAuth.register(mvc);
        for (int i = 1; i <= 10; i++) {
            mvc.perform(send(post(PROJECTS), token, project("Proyecto " + i, null, null, List.of())))
                    .andExpect(status().isCreated());
        }
        mvc.perform(send(post(PROJECTS), token, project("Proyecto 11", null, null, List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Puedes tener como máximo 10 proyectos. Borra uno para agregar otro."));
        mvc.perform(get(PROJECTS).with(TestAuth.bearer(token))).andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void invalidProjectsReturn400() throws Exception {
        String token = TestAuth.register(mvc);
        mvc.perform(send(post(PROJECTS), token, project(" ", null, null, List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El nombre del proyecto debe tener entre 1 y 60 caracteres."));
        mvc.perform(send(post(PROJECTS), token, project("x".repeat(61), null, null, List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El nombre del proyecto debe tener entre 1 y 60 caracteres."));
        mvc.perform(send(post(PROJECTS), token, project("P", "d".repeat(241), null, List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La descripción del proyecto puede tener como máximo 240 caracteres."));
        mvc.perform(send(post(PROJECTS), token, project("P", null, null, List.of("a", "b", "c", "d", "e", "f"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Un proyecto puede tener como máximo 5 etiquetas."));
        mvc.perform(send(post(PROJECTS), token, project("P", null, null, List.of("t".repeat(31)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cada etiqueta puede tener como máximo 30 caracteres."));
        mvc.perform(get(PROJECTS).with(TestAuth.bearer(token))).andExpect(jsonPath("$", hasSize(0)));

        // Los máximos exactos, con emojis contando como un carácter.
        mvc.perform(send(post(PROJECTS), token, project("🚀".repeat(60), "🚀".repeat(240), null,
                        List.of("🚀".repeat(30), "b", "c", "d", "e"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags", hasSize(5)));
    }

    // ---------- Aislamiento, sesión y borrado de cuenta ----------

    @Test
    void eachUserOnlySeesAndChangesTheirOwnItems() throws Exception {
        String ana = TestAuth.register(mvc);
        String luis = TestAuth.register(mvc);
        Long linkId = idOf(mvc.perform(send(post(LINKS), ana, link("GitHub", "https://github.com/ana")))
                .andReturn().getResponse().getContentAsString());
        Long projectId = idOf(mvc.perform(send(post(PROJECTS), ana, project("SkillMap", null, null, List.of("Java"))))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get(LINKS).with(TestAuth.bearer(luis))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(PROJECTS).with(TestAuth.bearer(luis))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(send(put(LINKS + "/" + linkId), luis, link("Robado", "https://evil.example")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el enlace."));
        mvc.perform(delete(LINKS + "/" + linkId).with(TestAuth.bearer(luis))).andExpect(status().isNotFound());
        mvc.perform(send(put(PROJECTS + "/" + projectId), luis, project("Robado", null, null, List.of())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el proyecto."));
        mvc.perform(delete(PROJECTS + "/" + projectId).with(TestAuth.bearer(luis))).andExpect(status().isNotFound());

        mvc.perform(get(LINKS).with(TestAuth.bearer(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("GitHub"))
                .andExpect(jsonPath("$[0].url").value("https://github.com/ana"));
        mvc.perform(get(PROJECTS).with(TestAuth.bearer(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("SkillMap"));
    }

    @Test
    void everyEndpointRequiresASession() throws Exception {
        String token = TestAuth.register(mvc);
        Long linkId = idOf(mvc.perform(send(post(LINKS), token, link("GitHub", "https://github.com/ana")))
                .andReturn().getResponse().getContentAsString());
        for (MockHttpServletRequestBuilder request : List.of(get(LINKS), post(LINKS), put(LINKS + "/" + linkId),
                delete(LINKS + "/" + linkId), get(PROJECTS), post(PROJECTS), put(PROJECTS + "/1"),
                delete(PROJECTS + "/1"))) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(link("X", "https://x.example")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get(LINKS).with(TestAuth.bearer("token-inventado"))).andExpect(status().isUnauthorized());
        mvc.perform(get(LINKS).with(TestAuth.bearer(token))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void deletingTheAccountDeletesItsLinksAndProjectsButNotOthers() throws Exception {
        String ana = TestAuth.register(mvc);
        String luis = TestAuth.register(mvc);
        Long anaId = sessions.findByTokenHash(tokens.hash(ana)).orElseThrow().userId();
        Long luisId = sessions.findByTokenHash(tokens.hash(luis)).orElseThrow().userId();
        for (String token : List.of(ana, luis)) {
            mvc.perform(send(post(LINKS), token, link("GitHub", "https://github.com/x"))).andExpect(status().isCreated());
            mvc.perform(send(post(PROJECTS), token, project("P", "D", null, List.of("Java", "Spring"))))
                    .andExpect(status().isCreated());
        }

        mvc.perform(delete("/api/auth/me").with(TestAuth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + TestAuth.PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(linksTable.countByUserId(anaId)).isZero();
        assertThat(projectsTable.countByUserId(anaId)).isZero();
        assertThat(linksTable.countByUserId(luisId)).isEqualTo(1);
        assertThat(projectsTable.countByUserId(luisId)).isEqualTo(1);
        // Sin etiquetas huérfanas: solo quedan las del proyecto de Luis.
        assertThat(jdbc.queryForObject("select count(*) from profile_project_tags t where not exists "
                + "(select 1 from profile_projects p where p.id = t.project_id)", Long.class)).isZero();
        Long luisProject = projectsTable.findByUserIdOrderByIdAsc(luisId).get(0).getId();
        assertThat(jdbc.queryForObject("select count(*) from profile_project_tags where project_id = ?",
                Long.class, luisProject)).isEqualTo(2);
    }

    // ---------- Ayudas ----------

    private static MockHttpServletRequestBuilder send(MockHttpServletRequestBuilder request, String token, String body) {
        return request.with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String link(String label, String url) {
        return "{\"label\":" + json(label) + ",\"url\":" + json(url) + "}";
    }

    private static String project(String name, String description, String url, List<String> tags) {
        String tagList = String.join(",", tags.stream().map(ProfileControllerTest::json).toList());
        return "{\"name\":" + json(name) + ",\"description\":" + json(description) + ",\"url\":" + json(url)
                + ",\"tags\":[" + tagList + "]}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Long idOf(String response) {
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
