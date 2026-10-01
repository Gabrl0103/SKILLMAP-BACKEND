package com.skillmap.api.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmap.api.domain.exception.AiInvalidResponseException;
import com.skillmap.api.domain.exception.AiNotConfiguredException;
import com.skillmap.api.domain.exception.AiQuotaExhaustedException;
import com.skillmap.api.domain.exception.AiRateLimitException;
import com.skillmap.api.domain.exception.AiServiceException;
import com.skillmap.api.domain.exception.AiTimeoutException;
import com.skillmap.api.infrastructure.config.GeminiApiKeyProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el adaptador contra un servidor HTTP local que imita a Gemini. */
class GeminiJobSkillExtractorTest {

    private static final String SKILLS_OK = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"skills\\":[\\"Java\\"]}"}]}}]}
            """;
    private static final String OVERLOADED = "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}";
    private static final String DAILY_QUOTA = """
            {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[{"violations":[
              {"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier","quotaValue":"20"}]}]}}
            """;
    private static final String PRIMARY_PATH = "/v1beta/models/gemini-test-model:generateContent";
    private static final String FALLBACK_PATH = "/v1beta/models/gemini-fallback-model:generateContent";

    private HttpServer server;
    /** Respuestas en orden; la última se repite para cualquier llamada extra. */
    private final Deque<Response> responses = new ConcurrentLinkedDeque<>();
    private final List<String> receivedPaths = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private volatile long bodyStallMillis;

    private record Response(int status, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedPaths.add(exchange.getRequestURI().getPath());
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Response response = responses.size() > 1 ? responses.poll() : responses.peek();
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            if (bodyStallMillis > 0) {
                // Imita a Gemini enviando el 200 y quedándose colgado a mitad del cuerpo.
                exchange.getResponseBody().write(bytes, 0, 1);
                exchange.getResponseBody().flush();
                try {
                    Thread.sleep(bodyStallMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.close();
                return;
            }
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GeminiJobSkillExtractor extractor(String apiKey) {
        return extractor(apiKey, Duration.ofSeconds(5), Duration.ofSeconds(35));
    }

    private GeminiJobSkillExtractor extractor(String apiKey, Duration attemptTimeout, Duration totalTimeout) {
        return extractor(apiKey, attemptTimeout, totalTimeout, Duration.ZERO);
    }

    private GeminiJobSkillExtractor extractor(String apiKey, Duration attemptTimeout, Duration totalTimeout,
                                              Duration retryDelay) {
        var env = new MockEnvironment();
        if (apiKey != null) {
            env.setProperty("GEMINI_API_KEY", apiKey);
        }
        return new GeminiJobSkillExtractor(RestClient.builder(), new GeminiApiKeyProvider(env), new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                "gemini-test-model", "gemini-fallback-model", attemptTimeout, totalTimeout, retryDelay);
    }

    private void respond(int status, String body) {
        responses.clear();
        responses.add(new Response(status, body));
    }

    private void respondInOrder(Response... sequence) {
        responses.clear();
        responses.addAll(List.of(sequence));
    }

    @Test
    void callsGenerateContentAndParsesFencedJson() {
        respond(200, """
                {"candidates":[{"content":{"parts":[
                  {"text":"```json\\n{\\"skills\\":[\\"React\\",\\"Docker\\"]}\\n```"}
                ]}}]}
                """);

        List<String> skills = extractor("test-key").extractSkills("Buscamos dev React", List.of("React", "SQL"));

        assertThat(skills).containsExactly("React", "Docker");
        assertThat(receivedPath.get()).isEqualTo("/v1beta/models/gemini-test-model:generateContent");
        assertThat(receivedKey.get()).isEqualTo("test-key");
        assertThat(receivedBody.get())
                .contains("Buscamos dev React")
                .contains("React, SQL")
                .contains("application/json");
    }

    @Test
    void failsClearlyWhenApiKeyIsMissing() {
        assertThatThrownBy(() -> extractor(null).extractSkills("oferta", List.of()))
                .isInstanceOf(AiNotConfiguredException.class)
                .hasMessageContaining("GEMINI_API_KEY");
        assertThat(receivedPath.get()).isNull();
    }

    @Test
    void treatsBlankApiKeyAsMissing() {
        assertThatThrownBy(() -> extractor("  ").extractSkills("oferta", List.of()))
                .isInstanceOf(AiNotConfiguredException.class);
    }

    @Test
    void mapsHttp429ToRateLimit() {
        respond(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");

        assertThatThrownBy(() -> extractor("test-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiRateLimitException.class)
                .hasMessageContaining("límite");
    }

    @Test
    void mapsInvalidKeyError() {
        respond(400, "{\"error\":{\"code\":400,\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}");

        assertThatThrownBy(() -> extractor("bad-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void mapsServerErrorToUnavailable() {
        respond(503, "{\"error\":{\"code\":503}}");

        assertThatThrownBy(() -> extractor("test-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    void overloadedPrimaryFallsBackToSecondModel() {
        respondInOrder(new Response(503, OVERLOADED), new Response(200, SKILLS_OK));

        List<String> skills = extractor("test-key").extractSkills("oferta", List.of());

        assertThat(skills).containsExactly("Java");
        assertThat(receivedPaths).containsExactly(PRIMARY_PATH, FALLBACK_PATH);
    }

    @Test
    void callsEachModelAtMostOnceAndKeepsUnavailableMessage() {
        respond(503, OVERLOADED);

        assertThatThrownBy(() -> extractor("test-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessage("Gemini no está disponible en este momento. Inténtalo de nuevo en unos minutos.");
        assertThat(receivedPaths).containsExactly(PRIMARY_PATH, FALLBACK_PATH);
    }

    @Test
    void dailyQuotaOnPrimaryJumpsToFallbackWithoutWaiting() {
        respondInOrder(new Response(429, DAILY_QUOTA), new Response(200, SKILLS_OK));
        // Con una espera de 10 s configurada, terminar rápido demuestra que no se esperó.
        var extractor = extractor("test-key", Duration.ofSeconds(5), Duration.ofSeconds(35), Duration.ofSeconds(10));

        long start = System.nanoTime();
        List<String> skills = extractor.extractSkills("oferta", List.of());

        assertThat(skills).containsExactly("Java");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertThat(receivedPaths).containsExactly(PRIMARY_PATH, FALLBACK_PATH);
    }

    @Test
    void dailyQuotaOnBothModelsGivesClearMessage() {
        respond(429, DAILY_QUOTA);

        assertThatThrownBy(() -> extractor("test-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiQuotaExhaustedException.class)
                .hasMessage("Se agotó el cupo diario gratuito de la IA, inténtalo mañana.");
        assertThat(receivedPaths).containsExactly(PRIMARY_PATH, FALLBACK_PATH);
    }

    @Test
    void dailyQuotaOnlyOnFallbackReportsPrimaryFailure() {
        respondInOrder(new Response(503, OVERLOADED), new Response(429, DAILY_QUOTA));

        assertThatThrownBy(() -> extractor("test-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    void sameOfferIsServedFromCacheWithoutCallingGemini() {
        respond(200, SKILLS_OK);
        var extractor = extractor("test-key");

        List<String> first = extractor.extractSkills("Oferta Java", List.of());
        List<String> second = extractor.extractSkills("Oferta Java", List.of());
        extractor.extractSkills("Otra oferta", List.of());

        assertThat(second).isEqualTo(first);
        assertThat(receivedPaths).hasSize(2);
    }

    @Test
    void failuresAreNotCached() {
        respond(500, "{\"error\":{\"code\":500}}");
        var extractor = extractor("test-key");
        assertThatThrownBy(() -> extractor.extractSkills("oferta", List.of()))
                .isInstanceOf(AiServiceException.class);

        respond(200, SKILLS_OK);

        assertThat(extractor.extractSkills("oferta", List.of())).containsExactly("Java");
        assertThat(receivedPaths).hasSize(2);
    }

    @Test
    void doesNotRetryOtherErrors() {
        respond(500, "{\"error\":{\"code\":500}}");

        assertThatThrownBy(() -> extractor("test-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiServiceException.class);
        assertThat(receivedPaths).hasSize(1);
    }

    @Test
    void doesNotRetryWhenTotalTimeIsNearlyUsedUp() {
        respond(503, OVERLOADED);

        assertThatThrownBy(() -> extractor("test-key", Duration.ofSeconds(5), Duration.ofSeconds(4))
                .extractSkills("oferta", List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("no está disponible");
        assertThat(receivedPaths).hasSize(1);
    }

    @Test
    void mapsBodyThatNeverFinishesToTimeout() {
        respond(200, SKILLS_OK);
        bodyStallMillis = 2000;

        assertThatThrownBy(() -> extractor("test-key", Duration.ofMillis(300), Duration.ofSeconds(35))
                .extractSkills("oferta", List.of()))
                .isInstanceOf(AiTimeoutException.class)
                .hasMessage("Gemini tardó demasiado, inténtalo de nuevo.");
        assertThat(receivedPaths).hasSize(1);
    }

    @Test
    void mapsGarbageAnswerToInvalidResponse() {
        respond(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Lo siento, no puedo.\"}]}}]}");

        assertThatThrownBy(() -> extractor("test-key").extractSkills("oferta", List.of()))
                .isInstanceOf(AiInvalidResponseException.class);
    }
}
