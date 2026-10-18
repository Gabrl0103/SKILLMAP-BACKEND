package com.skillmap.api.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.UserSkill;
import com.skillmap.api.domain.port.TokenGenerator;
import com.skillmap.api.domain.repository.SessionRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.domain.repository.UserSkillRepository;
import com.skillmap.api.infrastructure.persistence.AppSettingJpaRepository;
import com.skillmap.api.infrastructure.persistence.CareerGoalJpaRepository;
import com.skillmap.api.infrastructure.persistence.SessionJpaRepository;
import com.skillmap.api.infrastructure.persistence.SkillJpaRepository;
import com.skillmap.api.infrastructure.persistence.UserJpaRepository;
import com.skillmap.api.infrastructure.persistence.UserSkillJpaRepository;
import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cuenta del usuario de la sesión: PATCH /api/auth/me (nombre y titular), PUT /api/auth/me/password
 * y DELETE /api/auth/me. Base en memoria y carpeta temporal; nunca se llama a una API externa. Los
 * lectores/escritores de cuerpos de Spring en DEBUG para comprobar que las contraseñas no llegan a
 * los logs.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-account-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-account-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY=",
        "logging.level.org.springframework.web.servlet.mvc.method.annotation=DEBUG"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccountControllerTest {

    private static final String PASSWORD = "Secreta-De-Prueba-987";
    private static final String NEW_PASSWORD = "Nueva-Clave-De-Prueba-654";
    private static final String WRONG_PASSWORD = "La contraseña actual no es correcta.";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserJpaRepository usersTable;

    @Autowired
    SessionJpaRepository sessionsTable;

    @Autowired
    UserSkillJpaRepository userSkillsTable;

    @Autowired
    SkillJpaRepository skillsTable;

    @Autowired
    CareerGoalJpaRepository goalsTable;

    @Autowired
    AppSettingJpaRepository settingsTable;

    @Autowired
    SessionRepository sessions;

    @Autowired
    SkillRepository skills;

    @Autowired
    UserSkillRepository userSkills;

    @Autowired
    TokenGenerator tokens;

    // ---------- GET y PATCH /api/auth/me ----------

    @Test
    void newUserHasNoHeadline() throws Exception {
        String token = tokenOf(mvc.perform(register("Ana", uniqueEmail(), PASSWORD)));
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.headline").isEmpty());
    }

    @Test
    void patchChangesNameAndHeadline() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));

        mvc.perform(patchMe(token, "{\"name\":\"  Ana López  \",\"headline\":\"  Desarrolladora Frontend  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana López"))
                .andExpect(jsonPath("$.headline").value("Desarrolladora Frontend"))
                .andExpect(jsonPath("$.email").value(email));

        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token)))
                .andExpect(jsonPath("$.name").value("Ana López"))
                .andExpect(jsonPath("$.headline").value("Desarrolladora Frontend"));
        // El login también devuelve el titular.
        mvc.perform(login(email, PASSWORD)).andExpect(jsonPath("$.user.headline").value("Desarrolladora Frontend"));
    }

    @Test
    void patchOnlyChangesThePresentFields() throws Exception {
        String token = tokenOf(mvc.perform(register("Ana", uniqueEmail(), PASSWORD)));
        mvc.perform(patchMe(token, "{\"headline\":\"Backend Java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.headline").value("Backend Java"));

        mvc.perform(patchMe(token, "{\"name\":\"Ana María\"}"))
                .andExpect(jsonPath("$.name").value("Ana María"))
                .andExpect(jsonPath("$.headline").value("Backend Java"));

        mvc.perform(patchMe(token, "{\"name\":null,\"headline\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana María"))
                .andExpect(jsonPath("$.headline").value("Backend Java"));
    }

    @Test
    void emptyHeadlineRemovesIt() throws Exception {
        String token = tokenOf(mvc.perform(register("Ana", uniqueEmail(), PASSWORD)));
        mvc.perform(patchMe(token, "{\"headline\":\"Data Scientist\"}")).andExpect(status().isOk());

        mvc.perform(patchMe(token, "{\"headline\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").isEmpty());
    }

    @Test
    void acceptsTheMaximumLengthsCountingEmojisAsOneCharacter() throws Exception {
        String token = tokenOf(mvc.perform(register("Ana", uniqueEmail(), PASSWORD)));
        String name = "n".repeat(60);
        String headline = "😀".repeat(80);
        mvc.perform(patchMe(token, "{\"name\":\"" + name + "\",\"headline\":\"" + headline + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.headline").value(headline));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "{\"name\":\"\"}                 | El nombre debe tener entre 1 y 60 caracteres.",
            "{\"name\":\"   \"}              | El nombre debe tener entre 1 y 60 caracteres.",
            "{\"name\":\"%n\"}               | El nombre debe tener entre 1 y 60 caracteres.",
            "{\"name\":\"%e\"}               | El nombre es demasiado largo: con emojis caben menos de 60 caracteres.",
            "{\"headline\":\"%h\"}           | El titular puede tener como máximo 80 caracteres.",
            "{\"name\":\"Otra\",\"headline\":\"%h\"} | El titular puede tener como máximo 80 caracteres.",
            "{\"name\":[\"a\"]}              | El campo 'name' no tiene un valor válido.",
            "no-es-json                      | El cuerpo de la petición no es un JSON válido."
    })
    void invalidProfileReturns400AndChangesNothing(String template, String message) throws Exception {
        String token = tokenOf(mvc.perform(register("Ana", uniqueEmail(), PASSWORD)));
        String body = template.replace("%n", "n".repeat(61)).replace("%h", "h".repeat(81))
                .replace("%e", "😀".repeat(31));

        mvc.perform(patchMe(token, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));

        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token)))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.headline").isEmpty());
    }

    // ---------- PUT /api/auth/me/password ----------

    @Test
    void changingThePasswordKeepsThisSessionAndClosesTheOthers() throws Exception {
        String email = uniqueEmail();
        String current = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        String other = tokenOf(mvc.perform(login(email, PASSWORD)));
        String another = tokenOf(mvc.perform(login(email, PASSWORD)));
        String oldHash = usersTable.findByEmail(email).orElseThrow().getPasswordHash();
        // Otra cuenta no se ve afectada.
        String stranger = tokenOf(mvc.perform(register("Otro", uniqueEmail(), PASSWORD)));

        mvc.perform(changePassword(current, PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(current))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(other))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(another))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(stranger))).andExpect(status().isOk());
        assertThat(sessionsTable.findAll()).extracting("tokenHash")
                .contains(tokens.hash(current)).doesNotContain(tokens.hash(other), tokens.hash(another));

        String newHash = usersTable.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(newHash).startsWith("$2").isNotEqualTo(oldHash).doesNotContain(NEW_PASSWORD);
        mvc.perform(login(email, PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(login(email, NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordReturns400AndKeepsTheSession() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        String other = tokenOf(mvc.perform(login(email, PASSWORD)));

        mvc.perform(changePassword(token, "otra-clave-mala", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(WRONG_PASSWORD));
        mvc.perform(put("/api/auth/me/password").with(TestAuth.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(WRONG_PASSWORD));

        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(other))).andExpect(status().isOk());
        mvc.perform(login(email, PASSWORD)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "corta12 | La contraseña debe tener entre 8 y 72 caracteres.",
            "%long   | La contraseña debe tener entre 8 y 72 caracteres.",
            "%null   | La contraseña debe tener entre 8 y 72 caracteres.",
            "%bytes  | La contraseña es demasiado larga: con tildes o emojis caben menos de 72 caracteres."
    })
    void newPasswordFollowsTheRegistrationRules(String newPassword, String message) throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        String value = switch (newPassword) {
            case "%long" -> "p".repeat(73);
            case "%bytes" -> "ñ".repeat(40);
            case "%null" -> null;
            default -> newPassword;
        };
        String body = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":"
                + (value == null ? "null" : "\"" + value + "\"") + "}";

        mvc.perform(put("/api/auth/me/password").with(TestAuth.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(content().string(not(containsString(PASSWORD))));
        mvc.perform(login(email, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void passwordOfExactly8CharactersIsAccepted() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        mvc.perform(changePassword(token, PASSWORD, "12345678")).andExpect(status().isNoContent());
        mvc.perform(login(email, "12345678")).andExpect(status().isOk());
    }

    // ---------- Límite de intentos compartido con el login ----------

    @Test
    void wrongPasswordsCountInTheSameLimitAsTheLogin() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));

        // 5 fallos entre cambio de contraseña, borrado y login (misma IP y correo).
        mvc.perform(changePassword(token, "mala-1", NEW_PASSWORD)).andExpect(status().isBadRequest());
        mvc.perform(changePassword(token, "mala-2", NEW_PASSWORD)).andExpect(status().isBadRequest());
        mvc.perform(deleteMe(token, "mala-3")).andExpect(status().isBadRequest());
        mvc.perform(login(email, "mala-4")).andExpect(status().isUnauthorized());
        mvc.perform(login(email, "mala-5")).andExpect(status().isUnauthorized());

        // Bloqueado en los tres aunque ahora la contraseña sea la buena, y sin cerrar la sesión.
        String blocked = "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.";
        mvc.perform(login(email, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(blocked));
        mvc.perform(changePassword(token, PASSWORD, NEW_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(blocked));
        mvc.perform(deleteMe(token, PASSWORD)).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token))).andExpect(status().isOk());
        assertThat(usersTable.findByEmail(email)).isPresent();

        // Desde otra IP no está bloqueado.
        mvc.perform(changePassword(token, PASSWORD, NEW_PASSWORD).with(request -> {
            request.setRemoteAddr("10.0.0.3");
            return request;
        })).andExpect(status().isNoContent());
    }

    @Test
    void anInvalidNewPasswordDoesNotUseAnAttempt() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        for (int i = 0; i < 6; i++) {
            mvc.perform(changePassword(token, "mala-" + i, "corta")).andExpect(status().isBadRequest());
        }
        mvc.perform(login(email, PASSWORD)).andExpect(status().isOk());
    }

    // ---------- DELETE /api/auth/me ----------

    @Test
    void deletingTheAccountRemovesUserSessionsAndProgressButNothingGlobal() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        String other = tokenOf(mvc.perform(login(email, PASSWORD)));
        Long userId = sessions.findByTokenHash(tokens.hash(token)).orElseThrow().userId();
        String bob = TestAuth.register(mvc);
        Long bobId = sessions.findByTokenHash(tokens.hash(bob)).orElseThrow().userId();

        List<Long> skillIds = skills.findAll().stream().map(s -> s.getId()).limit(2).toList();
        mvc.perform(patch("/api/skills/" + skillIds.get(0) + "/master").with(TestAuth.bearer(token)))
                .andExpect(status().isOk());
        userSkills.save(new UserSkill(userId, skillIds.get(1), SkillStatus.IN_PROGRESS));
        mvc.perform(patch("/api/skills/" + skillIds.get(0) + "/master").with(TestAuth.bearer(bob)))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/settings").with(TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"dark\"}")).andExpect(status().isOk());
        assertThat(userSkills.findStatusesByUserId(userId)).hasSize(2);

        long skillCount = skillsTable.count();
        long goalCount = goalsTable.count();
        long settingCount = settingsTable.count();
        List<String> skillsBefore = skills.findAll().stream().map(s -> s.getName() + "=" + s.getDemandPercentage()).toList();

        mvc.perform(deleteMe(token, PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(usersTable.findById(userId)).isEmpty();
        assertThat(sessionsTable.findAll()).noneMatch(s -> s.getUserId().equals(userId));
        assertThat(userSkillsTable.findAll()).noneMatch(s -> s.getUserId().equals(userId));
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(other))).andExpect(status().isUnauthorized());
        mvc.perform(login(email, PASSWORD)).andExpect(status().isUnauthorized());

        // Lo global y lo de los demás sigue igual.
        assertThat(skillsTable.count()).isEqualTo(skillCount);
        assertThat(skills.findAll().stream().map(s -> s.getName() + "=" + s.getDemandPercentage()).toList())
                .isEqualTo(skillsBefore);
        assertThat(goalsTable.count()).isEqualTo(goalCount);
        assertThat(settingsTable.count()).isEqualTo(settingCount);
        assertThat(userSkills.findStatusesByUserId(bobId)).containsEntry(skillIds.get(0), SkillStatus.MASTERED);
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(bob))).andExpect(status().isOk());
        mvc.perform(get("/api/settings").with(TestAuth.bearer(bob))).andExpect(jsonPath("$.theme").value("dark"));

        // El correo queda libre: se puede volver a registrar y empieza sin progreso.
        String again = tokenOf(mvc.perform(register("Ana", email, PASSWORD)).andExpect(status().isCreated()));
        mvc.perform(get("/api/skills").with(TestAuth.bearer(again)))
                .andExpect(jsonPath("$[?(@.status == 'MASTERED')]").isEmpty())
                .andExpect(jsonPath("$[?(@.status == 'IN_PROGRESS')]").isEmpty());
    }

    @Test
    void deleteWithWrongOrMissingPasswordKeepsEverything() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        Long userId = sessions.findByTokenHash(tokens.hash(token)).orElseThrow().userId();
        Long skillId = skills.findAll().get(0).getId();
        mvc.perform(patch("/api/skills/" + skillId + "/master").with(TestAuth.bearer(token))).andExpect(status().isOk());

        mvc.perform(deleteMe(token, "otra-clave-mala"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(WRONG_PASSWORD));
        mvc.perform(delete("/api/auth/me").with(TestAuth.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(WRONG_PASSWORD));
        mvc.perform(delete("/api/auth/me").with(TestAuth.bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es un JSON válido."));

        assertThat(usersTable.findById(userId)).isPresent();
        assertThat(userSkills.findStatusesByUserId(userId)).containsEntry(skillId, SkillStatus.MASTERED);
        mvc.perform(get("/api/auth/me").with(TestAuth.bearer(token))).andExpect(status().isOk());
    }

    // ---------- Sin sesión ----------

    @Test
    void allThreeRequireAValidSession() throws Exception {
        String body = "{\"name\":\"X\",\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD
                + "\",\"password\":\"" + PASSWORD + "\"}";
        List<MockHttpServletRequestBuilder> requests = List.of(
                patch("/api/auth/me"), put("/api/auth/me/password"), delete("/api/auth/me"));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Sesión no válida o expirada. Inicia sesión de nuevo."));
        }
        for (MockHttpServletRequestBuilder request : List.of(
                patch("/api/auth/me"), put("/api/auth/me/password"), delete("/api/auth/me"))) {
            mvc.perform(request.with(TestAuth.bearer("token-que-no-existe"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------- Secretos fuera de logs y respuestas ----------

    @Test
    void passwordsNeverReachTheLogs(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(mvc.perform(register("Ana", email, PASSWORD)));
        mvc.perform(changePassword(token, PASSWORD + "-mal", NEW_PASSWORD)).andExpect(status().isBadRequest());
        mvc.perform(changePassword(token, PASSWORD, NEW_PASSWORD)).andExpect(status().isNoContent());
        mvc.perform(deleteMe(token, NEW_PASSWORD + "-mal")).andExpect(status().isBadRequest());
        mvc.perform(deleteMe(token, NEW_PASSWORD)).andExpect(status().isNoContent());

        // Comprueba que el DEBUG de Spring sí está activo: si no, la prueba no probaría nada.
        assertThat(output.getAll()).contains("ChangePasswordRequest[").contains("DeleteAccountRequest[");
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD).doesNotContain(token);
    }

    // ---------- Utilidades ----------

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    private static MockHttpServletRequestBuilder register(String name, String email, String password) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder patchMe(String token, String body) {
        return patch("/api/auth/me").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder changePassword(String token, String current, String next) {
        return put("/api/auth/me/password").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    }

    private static MockHttpServletRequestBuilder deleteMe(String token, String password) {
        return delete("/api/auth/me").with(TestAuth.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}");
    }

    private static String tokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }
}
