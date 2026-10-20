package com.skillmap.api.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.skillmap.api.domain.exception.AiDailyLimitException;
import com.skillmap.api.domain.exception.AiInvalidResponseException;
import com.skillmap.api.domain.exception.AiServiceException;
import com.skillmap.api.domain.exception.AiTimeoutException;
import com.skillmap.api.domain.port.JobSkillExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Adaptador del puerto JobSkillExtractor contra el servicio de IA de SkillMap en la nube
 * (perfil cloud, POST /api/ai/extract-skills): así el Analizador funciona sin key propia.
 * El servicio no recibe el catálogo. En los logs solo aparece el host, nunca la URL ni la oferta.
 */
@Component
public class RemoteJobSkillExtractor implements JobSkillExtractor {

    private static final Logger log = LoggerFactory.getLogger(RemoteJobSkillExtractor.class);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** El plan gratis de Render tarda 30-60 s en despertar, y luego está la propia llamada a Gemini. */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(90);
    static final String DAILY_LIMIT_MESSAGE = "Se alcanzó el límite diario de la IA. Inténtalo de nuevo mañana.";
    static final String UNAVAILABLE_MESSAGE = "El servicio de IA no está disponible. Inténtalo de nuevo en un minuto.";
    static final String OFFLINE_MESSAGE = "No se pudo conectar con el servicio de IA. Revisa tu conexión a internet.";

    private final RestClient restClient;
    private final String host;

    @Autowired
    public RemoteJobSkillExtractor(RestClient.Builder restClientBuilder,
                                   @Value("${skillmap.ai.remote-url}") String remoteUrl) {
        this(restClientBuilder.clone()
                .baseUrl(remoteUrl.replaceAll("/+$", ""))
                .requestFactory(requestFactory())
                .build(), URI.create(remoteUrl).getHost());
    }

    /** Los tests pasan un RestClient enlazado a MockRestServiceServer. */
    RemoteJobSkillExtractor(RestClient restClient, String host) {
        this.restClient = restClient;
        this.host = host;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public List<String> extractSkills(String jobDescription, Collection<String> knownSkills) {
        RemoteSkills response;
        try {
            response = restClient.post()
                    .uri("/api/ai/extract-skills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("jobDescription", jobDescription))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        log.warn("El servicio de IA ({}) respondió HTTP {}", host, status);
                        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                            throw new AiDailyLimitException(DAILY_LIMIT_MESSAGE);
                        }
                        if (res.getStatusCode().is5xxServerError()) {
                            throw new AiServiceException(UNAVAILABLE_MESSAGE);
                        }
                        throw new AiServiceException("El servicio de IA rechazó la solicitud (HTTP " + status + ").");
                    })
                    .body(RemoteSkills.class);
        } catch (RestClientException e) {
            // Como causa solo la de bajo nivel: el mensaje de RestClient incluye la URL completa.
            Throwable root = rootCause(e);
            if (root instanceof SocketTimeoutException) {
                log.warn("El servicio de IA ({}) no respondió a tiempo", host);
                throw new AiTimeoutException(UNAVAILABLE_MESSAGE, root);
            }
            log.warn("No se pudo conectar con el servicio de IA ({}): {}", host, root.getClass().getSimpleName());
            throw new AiServiceException(OFFLINE_MESSAGE);
        }
        if (response == null || response.skills() == null) {
            throw new AiInvalidResponseException("El servicio de IA devolvió una respuesta inválida.");
        }
        return List.copyOf(response.skills());
    }

    @Override
    public String engine() {
        return "remote";
    }

    /** GET /api/health para despertar el servicio. Cualquier fallo se ignora: solo es un aviso previo. */
    public void warmUp() {
        try {
            restClient.get().uri("/api/health").retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.debug("No se pudo despertar el servicio de IA ({})", host);
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RemoteSkills(List<String> skills) {
    }
}
