package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.model.Session;
import com.skillmap.api.domain.port.TokenGenerator;
import com.skillmap.api.domain.repository.SessionRepository;
import com.skillmap.api.infrastructure.persistence.SessionJpaRepository;
import com.skillmap.api.infrastructure.persistence.UserJpaRepository;
import com.skillmap.api.infrastructure.persistence.entity.SessionEntity;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro, login, sesiones y protección de /api/** de punta a punta. Base en memoria y carpeta
 * temporal; los lectores/escritores de cuerpos de Spring en DEBUG para comprobar que ni así
 * aparecen la contraseña o el token en los logs.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-auth-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-auth-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY=",
        "logging.level.org.springframework.web.servlet.mvc.method.annotation=DEBUG"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {

    private static final String PASSWORD = "Secreta-De-Prueba-987";
    private static final String INVALID_CREDENTIALS = "Correo o contraseña incorrectos";

    @Autowired
    MockMvc mvc;

    @Autowired
    SessionJpaRepository sessionsTable;

    @Autowired
    UserJpaRepository usersTable;

    @Autowired
    SessionRepository sessions;

    @Autowired
    TokenGenerator tokens;

    // ---------- Registro ----------

    @Test
    void registerReturnsTokenAndUserButNeverThePassword() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register("  Ana  ", email.toUpperCase(), PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", hasLength(43)))
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.name").value("Ana"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))));

        String storedHash = usersTable.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(storedHash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register("Ana", email, PASSWORD)).andExpect(status().isCreated());

        mvc.perform(register("Otra", email.toUpperCase(), PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con ese correo."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"Ana\",\"email\":\"ana@test.local\",\"password\":\"corta12\"}",
            "{\"name\":\"Ana\",\"email\":\"ana@test.local\",\"password\":\"%s\"}",
            "{\"name\":\"Ana\",\"email\":\"no-es-un-correo\",\"password\":\"Secreta-De-Prueba-987\"}",
            "{\"name\":\"Ana\",\"email\":\"%e\",\"password\":\"Secreta-De-Prueba-987\"}",
            "{\"name\":\"   \",\"email\":\"ana@test.local\",\"password\":\"Secreta-De-Prueba-987\"}",
            "{\"name\":\"%n\",\"email\":\"ana@test.local\",\"password\":\"Secreta-De-Prueba-987\"}",
            "{}"
    })
    void invalidRegistrationsReturn400WithMessage(String template) throws Exception {
        String longPassword = "p".repeat(73);
        String body = template.replace("%s", longPassword)
                .replace("%e", "a".repeat(245) + "@test.local")
                .replace("%n", "n".repeat(61));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(content().string(not(containsString(longPassword))))
                .andExpect(content().string(not(containsString("corta12"))));
    }

    @Test
    void passwordOfExactly8And72CharactersIsAccepted() throws Exception {
        mvc.perform(register("Ana", uniqueEmail(), "12345678")).andExpect(status().isCreated());
        mvc.perform(register("Ana", uniqueEmail(), "x".repeat(72))).andExpect(status().isCreated());
    }

    // ---------- Login ----------

    @Test
    void loginWithCorrectCredentialsOpensANewSession() throws Exception {
        String email = uniqueEmail();
        String registerToken = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));

        String loginToken = tokenOf(mvc.perform(login(email.toUpperCase(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email)));

        assertThat(loginToken).isNotEqualTo(registerToken);
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(loginToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Ana"));
    }

    @Test
    void failedLoginAlwaysSaysTheSame() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register("Ana", email, PASSWORD)).andExpect(status().isCreated());

        mvc.perform(login(email, "otra-clave-incorrecta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
        mvc.perform(login(uniqueEmail(), PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void blocksAfterFiveFailedLoginsForTheSameEmailAndIp() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register("Ana", email, PASSWORD)).andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            mvc.perform(login(email, "incorrecta-" + i)).andExpect(status().isUnauthorized());
        }
        // Bloqueado aunque ahora la contraseña sea la buena.
        mvc.perform(login(email, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").isString());

        // Otra IP, u otro correo desde la misma IP, no están bloqueados.
        mvc.perform(login(email, PASSWORD).with(request -> {
            request.setRemoteAddr("10.0.0.2");
            return request;
        })).andExpect(status().isOk());
        mvc.perform(login(uniqueEmail(), PASSWORD)).andExpect(status().isUnauthorized());
    }

    // ---------- Sesiones ----------

    @Test
    void onlyTheTokenHashIsStoredAndItExpiresIn30Days() throws Exception {
        String token = tokenOf(mvc.perform(register("Ana", uniqueEmail(), PASSWORD)));

        List<String> storedHashes = sessionsTable.findAll().stream().map(SessionEntity::getTokenHash).toList();
        assertThat(storedHashes).doesNotContain(token).contains(tokens.hash(token));
        assertThat(tokens.hash(token)).matches("[0-9a-f]{64}");

        SessionEntity session = sessionsTable.findById(tokens.hash(token)).orElseThrow();
        assertThat(Duration.between(Instant.now(), session.getExpiresAt()).toMinutes())
                .isCloseTo(Duration.ofDays(30).toMinutes(), within(5L));
    }

    @Test
    void expiredSessionIsRejectedAndRemoved() throws Exception {
        String registerToken = tokenOf(mvc.perform(register("Ana", uniqueEmail(), PASSWORD)));
        Long userId = sessions.findByTokenHash(tokens.hash(registerToken)).orElseThrow().userId();

        String expired = tokens.generate();
        sessions.save(new Session(tokens.hash(expired), userId, Instant.now().minusSeconds(1)));

        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isString());
        assertThat(sessionsTable.findById(tokens.hash(expired))).isEmpty();
    }

    @Test
    void logoutClosesOnlyThatSession() throws Exception {
        String email = uniqueEmail();
        String first = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        String second = tokenOf(mvc.perform(login(email, PASSWORD)));

        mvc.perform(post("/api/auth/logout").with(TestAuth.bearer(first)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(first))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").with(TestAuth.bearer(first))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(second))).andExpect(status().isOk());
    }

    // ---------- Protección de la API ----------

    @Test
    void protectedEndpointsRequireAValidSession() throws Exception {
        String[] paths = {"/api/skills", "/api/skills/1", "/api/goals", "/api/goals/1/readiness",
                "/api/settings", "/api/jobs/stats", "/api/auth/me"};
        String[] badHeaders = {null, "Bearer token-que-no-existe", "Basic dXNlcjpwYXNz", "Bearer ", "Bearer a b"};
        for (String path : paths) {
            for (String header : badHeaders) {
                var request = get(path);
                if (header != null) {
                    request.header(HttpHeaders.AUTHORIZATION, header);
                }
                mvc.perform(request)
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.message").value(
                                "Sesión no válida o expirada. Inicia sesión de nuevo."));
            }
        }
        mvc.perform(post("/api/analyzer/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":\"React\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs/sync")).andExpect(status().isUnauthorized());
        // Variantes de la ruta no se saltan el filtro.
        mvc.perform(get("/api//skills")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/skills;jsessionid=x")).andExpect(status().isUnauthorized());
    }

    @Test
    void staticFrontendFilesDoNotNeedASession() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().is(not(401)));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void cloudAiEndpointDoesNotExistOnDesktop() throws Exception {
        mvc.perform(post("/api/ai/extract-skills").with(TestAuth.bearer(TestAuth.register(mvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":\"React\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- Secretos fuera de logs y respuestas ----------

    @Test
    void passwordAndTokenNeverReachTheLogs(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        List<String> issued = new ArrayList<>();
        issued.add(tokenOf(mvc.perform(register("Ana", email, PASSWORD))));
        mvc.perform(login(email, PASSWORD + "-mal")).andExpect(status().isUnauthorized());
        issued.add(tokenOf(mvc.perform(login(email, PASSWORD))));
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(issued.get(1)))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").with(TestAuth.bearer(issued.get(1)))).andExpect(status().isNoContent());

        // Comprueba que el DEBUG de Spring sí está activo: si no, la prueba no probaría nada.
        assertThat(output.getAll()).contains("RegisterRequest[").contains("AuthResponse[");
        assertThat(output.getAll()).doesNotContain(PASSWORD);
        for (String token : issued) {
            assertThat(output.getAll()).doesNotContain(token);
        }
    }

    // ---------- Utilidades ----------

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String name, String email, String password) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static String tokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }
}
