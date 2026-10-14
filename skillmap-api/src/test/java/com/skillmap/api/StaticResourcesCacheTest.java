package com.skillmap.api;

import com.skillmap.api.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los estáticos del frontend van con "Cache-Control: no-cache": el navegador los revalida siempre (304 si no
 * cambiaron), así que tras actualizar la app no se queda con JS o CSS viejo. Se usan archivos propios del test
 * (static/ lo llena el script de empaquetado y no está en el repositorio).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skillmap-static-cache-test;DB_CLOSE_DELAY=-1",
        "skillmap.home=${java.io.tmpdir}/skillmap-static-cache-test",
        "skillmap.desktop.open-browser=false",
        "jobs.sync.on-first-start=false",
        "jobs.sync.cron=-",
        "spring.jpa.show-sql=false",
        "GEMINI_API_KEY=",
        "spring.web.resources.static-locations=classpath:/static-test/"
})
@AutoConfigureMockMvc
class StaticResourcesCacheTest {

    @Autowired
    MockMvc mvc;

    @Test
    void staticFilesMustBeRevalidated() throws Exception {
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }

    @Test
    void unchangedFileAnswersNotModified() throws Exception {
        MvcResult first = mvc.perform(get("/js/app.js")).andExpect(status().isOk()).andReturn();
        String lastModified = first.getResponse().getHeader(HttpHeaders.LAST_MODIFIED);
        assertThat(lastModified).isNotBlank();

        mvc.perform(get("/js/app.js").header(HttpHeaders.IF_MODIFIED_SINCE, lastModified))
                .andExpect(status().isNotModified());
    }

    @Test
    void apiResponsesAreNotAffected() throws Exception {
        String token = TestAuth.register(mvc);
        MvcResult result = mvc.perform(get("/api/skills").with(TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isNull();
    }
}
