package com.skillmap.api.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmap.api.domain.exception.AiInvalidResponseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiResponseParser parser = new GeminiResponseParser(mapper);

    @Test
    void parsesPlainJsonObject() {
        assertThat(parser.parseSkills("{\"skills\": [\"React\", \"Docker\"]}"))
                .containsExactly("React", "Docker");
    }

    @Test
    void toleratesMarkdownFences() {
        String text = "```json\n{\"skills\": [\"Java\", \"Spring Boot\"]}\n```";
        assertThat(parser.parseSkills(text)).containsExactly("Java", "Spring Boot");
    }

    @Test
    void toleratesBareArrayAndSurroundingText() {
        assertThat(parser.parseSkills("Aquí tienes: [\"AWS\", \"Terraform\"] ¡listo!"))
                .containsExactly("AWS", "Terraform");
    }

    @Test
    void dropsBlanksNonStringsAndCaseInsensitiveDuplicates() {
        assertThat(parser.parseSkills("{\"skills\": [\"React\", \"react\", \" \", 3, \"SQL \"]}"))
                .containsExactly("React", "SQL");
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> parser.parseSkills("no sé qué decirte"))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void rejectsJsonWithoutSkillsArray() {
        assertThatThrownBy(() -> parser.parseSkills("{\"skills\": \"React\"}"))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void extractsTextSkippingThoughtParts() throws Exception {
        var response = mapper.readTree("""
                {"candidates":[{"content":{"parts":[
                  {"text":"pensando...","thought":true},
                  {"text":"{\\"skills\\":[\\"Kotlin\\"]}"}
                ]}}]}
                """);
        assertThat(parser.extractText(response)).isEqualTo("{\"skills\":[\"Kotlin\"]}");
    }

    @Test
    void rejectsBlockedPrompt() throws Exception {
        var response = mapper.readTree("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertThatThrownBy(() -> parser.extractText(response))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("SAFETY");
    }

    @Test
    void rejectsEmptyCandidates() throws Exception {
        assertThatThrownBy(() -> parser.extractText(mapper.readTree("{\"candidates\":[]}")))
                .isInstanceOf(AiInvalidResponseException.class);
    }
}
