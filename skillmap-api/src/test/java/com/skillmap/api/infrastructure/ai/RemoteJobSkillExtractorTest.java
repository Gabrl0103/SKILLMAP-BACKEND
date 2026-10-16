package com.skillmap.api.infrastructure.ai;

import com.skillmap.api.domain.exception.AiDailyLimitException;
import com.skillmap.api.domain.exception.AiInvalidResponseException;
import com.skillmap.api.domain.exception.AiServiceException;
import com.skillmap.api.domain.exception.AiTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** El servicio en la nube está simulado con MockRestServiceServer: nunca se llama a Render. */
class RemoteJobSkillExtractorTest {

    private static final String BASE_URL = "http://ai.test";
    private static final String EXTRACT_URL = BASE_URL + "/api/ai/extract-skills";

    private MockRestServiceServer server;
    private RemoteJobSkillExtractor extractor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        extractor = new RemoteJobSkillExtractor(builder.build(), "ai.test");
    }

    @Test
    void returnsTheSkillsFromTheService() {
        server.expect(requestTo(EXTRACT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"jobDescription\":\"Buscamos Java y Docker\"}", true))
                .andRespond(withSuccess("{\"skills\":[\"Java\",\"Docker\"],\"engine\":\"gemini\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(extractor.extractSkills("Buscamos Java y Docker", List.of("React")))
                .containsExactly("Java", "Docker");
        server.verify();
    }

    @Test
    void tooManyRequestsMeansTheDailyLimit() {
        server.expect(requestTo(EXTRACT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Límite\"}"));

        assertThatThrownBy(() -> extractor.extractSkills("Java", List.of()))
                .isInstanceOf(AiDailyLimitException.class)
                .hasMessage(RemoteJobSkillExtractor.DAILY_LIMIT_MESSAGE);
    }

    @Test
    void serviceUnavailableAsksToTryAgainInAMinute() {
        server.expect(requestTo(EXTRACT_URL)).andRespond(withServiceUnavailable());

        assertThatThrownBy(() -> extractor.extractSkills("Java", List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(RemoteJobSkillExtractor.UNAVAILABLE_MESSAGE);
    }

    @Test
    void timeoutAsksToTryAgainInAMinuteWithoutTheUrl() {
        server.expect(requestTo(EXTRACT_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> extractor.extractSkills("Java", List.of()))
                .isInstanceOf(AiTimeoutException.class)
                .hasMessage(RemoteJobSkillExtractor.UNAVAILABLE_MESSAGE)
                // La causa no es la de RestClient, cuyo mensaje lleva la URL completa.
                .cause().isInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void noNetworkSaysSo() {
        server.expect(requestTo(EXTRACT_URL)).andRespond(withException(new UnknownHostException("ai.test")));

        assertThatThrownBy(() -> extractor.extractSkills("Java", List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(RemoteJobSkillExtractor.OFFLINE_MESSAGE)
                .hasNoCause();
    }

    @Test
    void responseWithoutSkillsIsInvalid() {
        server.expect(requestTo(EXTRACT_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extractSkills("Java", List.of()))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void warmUpCallsHealthAndIgnoresErrors() {
        server.expect(requestTo(BASE_URL + "/api/health")).andExpect(method(HttpMethod.GET))
                .andRespond(withServiceUnavailable());

        extractor.warmUp();
        server.verify();
    }
}
