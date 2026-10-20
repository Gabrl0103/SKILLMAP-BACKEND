package com.skillmap.api.infrastructure.ai;

import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.infrastructure.config.GeminiApiKeyProvider;
import com.skillmap.api.infrastructure.config.PropertiesFileGeminiKeyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Gemini es un fake y el servicio remoto está simulado: ni Render ni Gemini se llaman de verdad. */
class SelectingJobSkillExtractorTest {

    @TempDir
    Path configDir;

    private final MockEnvironment environment = new MockEnvironment();
    private final JobSkillExtractor gemini = (description, known) -> List.of("Desde Gemini");
    private MockRestServiceServer server;
    private SelectingJobSkillExtractor selector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        var keyProvider = new GeminiApiKeyProvider(new PropertiesFileGeminiKeyStore(environment, configDir));
        selector = new SelectingJobSkillExtractor(keyProvider, gemini,
                new RemoteJobSkillExtractor(builder.build(), "ai.test"));
    }

    @Test
    void withoutOwnKeyUsesTheRemoteService() {
        server.expect(requestTo("http://ai.test/api/ai/extract-skills"))
                .andRespond(withSuccess("{\"skills\":[\"Desde la nube\"]}", MediaType.APPLICATION_JSON));

        assertThat(selector.extractSkills("Java", List.of())).containsExactly("Desde la nube");
        server.verify();
    }

    @Test
    void withKeyFromEnvironmentUsesGemini() {
        environment.setProperty("GEMINI_API_KEY", "own-key");
        server.expect(never(), requestTo("http://ai.test/api/ai/extract-skills"));

        assertThat(selector.extractSkills("Java", List.of())).containsExactly("Desde Gemini");
        server.verify();
    }

    @Test
    void engineNamesTheSelectedExtractor() {
        assertThat(selector.engine()).isEqualTo("remote");

        environment.setProperty("GEMINI_API_KEY", "own-key");

        assertThat(selector.engine()).isEqualTo(gemini.engine());
    }

    @Test
    void keySavedInSettingsIsUsedWithoutRestarting() throws Exception {
        assertThat(selector.selected()).isNotSameAs(gemini);

        Files.writeString(configDir.resolve("config.properties"), "gemini.api.key=own-key\n");

        assertThat(selector.selected()).isSameAs(gemini);
    }
}
