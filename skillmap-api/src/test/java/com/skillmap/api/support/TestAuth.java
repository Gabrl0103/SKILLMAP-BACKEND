package com.skillmap.api.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Crea cuentas de prueba por la API y añade su token a las peticiones. */
public final class TestAuth {

    public static final String PASSWORD = "clave-de-prueba-123";

    private TestAuth() {
    }

    /** Registra un usuario con un correo único y devuelve su token. */
    public static String register(MockMvc mvc) throws Exception {
        return register(mvc, "user-" + UUID.randomUUID() + "@test.local");
    }

    public static String register(MockMvc mvc, String email) throws Exception {
        String body = "{\"name\":\"Prueba\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    public static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
