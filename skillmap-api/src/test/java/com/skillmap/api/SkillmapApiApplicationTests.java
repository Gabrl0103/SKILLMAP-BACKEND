package com.skillmap.api;

import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El contexto completo arranca y la API queda cerrada a otros orígenes. Base en memoria,
 * sin abrir el navegador y sin la sincronización inicial (no llama a Remotive ni a
 * Arbeitnow); Gemini solo se llama al analizar.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-context-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-context-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class SkillmapApiApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment environment;

    @Test
    void listensOnlyOnThisMachineByDefault() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void allowsSameOrigin() throws Exception {
        // MockMvc atiende en http://localhost: el frontend servido por la app.
        mvc.perform(get("/api/skills").header(HttpHeaders.ORIGIN, "http://localhost")
                        .with(TestAuth.bearer(TestAuth.register(mvc))))
                .andExpect(status().isOk());
    }

    @Test
    void allowsLiveServerOrigins() throws Exception {
        String token = TestAuth.register(mvc);
        for (String origin : new String[] {"http://localhost:5500", "http://127.0.0.1:5500"}) {
            mvc.perform(get("/api/skills").header(HttpHeaders.ORIGIN, origin).with(TestAuth.bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
    }

    @Test
    void allowsPreflightFromLiveServer() throws Exception {
        mvc.perform(options("/api/skills/1/master")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"));
    }

    @Test
    void preflightAllowsAuthorizationHeaderWithoutToken() throws Exception {
        mvc.perform(options("/api/skills")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("authorization")));
    }

    @Test
    void unauthorizedResponseKeepsCorsHeadersForAllowedOrigins() throws Exception {
        // Sin las cabeceras CORS, el frontend en Live Server no podría leer el 401.
        mvc.perform(get("/api/skills").header(HttpHeaders.ORIGIN, "http://localhost:5500"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(get("/api/skills").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(options("/api/skills/1/master")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsOtherPortsOnLocalhost() throws Exception {
        mvc.perform(get("/api/skills").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isForbidden());
    }
}
