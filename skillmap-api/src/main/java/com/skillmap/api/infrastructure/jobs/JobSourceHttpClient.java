package com.skillmap.api.infrastructure.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillmap.api.domain.exception.JobSourceException;
import com.skillmap.api.domain.exception.JobSourceInvalidResponseException;
import com.skillmap.api.domain.exception.JobSourceRateLimitException;
import com.skillmap.api.domain.exception.JobSourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;

/**
 * GET de JSON contra una bolsa de empleo, compartido por todos sus adaptadores.
 * Traduce cada fallo HTTP a una excepción de dominio con un mensaje en español.
 */
@Component
public class JobSourceHttpClient {

    private static final Logger log = LoggerFactory.getLogger(JobSourceHttpClient.class);
    private static final String USER_AGENT = "SkillMap/0.1";

    private final RestClient restClient;

    public JobSourceHttpClient(RestClient.Builder restClientBuilder,
                               @Value("${jobs.http.timeout}") Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    public JsonNode getJson(String source, URI uri) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw toException(source, res.getStatusCode());
                    })
                    .body(JsonNode.class);
        } catch (JobSourceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new JobSourceUnavailableException(
                        source + " tardó demasiado en responder. Inténtalo más tarde.", e);
            }
            throw new JobSourceUnavailableException(
                    "No se pudo conectar con " + source + ". Revisa tu conexión o inténtalo más tarde.", e);
        } catch (RestClientException e) {
            // Llegó una respuesta 200, pero no es JSON (por ejemplo, una página HTML de mantenimiento).
            throw new JobSourceInvalidResponseException(
                    source + " devolvió una respuesta que no es JSON válido.", e);
        }
        if (body == null) {
            throw new JobSourceInvalidResponseException(source + " devolvió una respuesta vacía.");
        }
        return body;
    }

    private static JobSourceException toException(String source, HttpStatusCode status) {
        log.warn("{} respondió HTTP {}", source, status.value());
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new JobSourceRateLimitException(
                    source + " alcanzó su límite de uso. Inténtalo de nuevo en unas horas.");
        }
        if (status.is5xxServerError()) {
            return new JobSourceUnavailableException(
                    source + " no está disponible en este momento (HTTP " + status.value()
                            + "). Inténtalo más tarde.");
        }
        return new JobSourceException(
                source + " rechazó la petición (HTTP " + status.value() + "). Puede que su API haya cambiado.");
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
