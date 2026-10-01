package com.skillmap.api.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmap.api.domain.exception.AiInvalidResponseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte la respuesta cruda de generateContent en una lista de habilidades.
 * Es tolerante: acepta el JSON envuelto en fences de markdown, con texto
 * alrededor, o como array suelto en lugar de {"skills": [...]}.
 */
final class GeminiResponseParser {

    private static final String INVALID_MESSAGE =
            "La IA devolvió una respuesta que no se pudo interpretar. Inténtalo de nuevo.";
    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z]*\\s*([\\s\\S]*?)```");
    private static final int MAX_SKILL_NAME_LENGTH = 60;

    private final ObjectMapper objectMapper;

    GeminiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Saca el texto generado del sobre de generateContent (candidates[0].content.parts[].text). */
    String extractText(JsonNode response) {
        if (response == null) {
            throw new AiInvalidResponseException(INVALID_MESSAGE);
        }
        String blockReason = response.path("promptFeedback").path("blockReason").asText("");
        if (!blockReason.isEmpty()) {
            throw new AiInvalidResponseException(
                    "Gemini bloqueó el análisis de la oferta (motivo: " + blockReason + ").");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            // Las partes de razonamiento interno no forman parte de la respuesta.
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        if (text.toString().isBlank()) {
            throw new AiInvalidResponseException(INVALID_MESSAGE);
        }
        return text.toString();
    }

    List<String> parseSkills(String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripToJson(text));
        } catch (JsonProcessingException e) {
            throw new AiInvalidResponseException(INVALID_MESSAGE, e);
        }

        JsonNode array = root.isArray() ? root : root.path("skills");
        if (!array.isArray()) {
            throw new AiInvalidResponseException(INVALID_MESSAGE);
        }

        List<String> skills = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : array) {
            if (!item.isTextual()) {
                continue;
            }
            String name = item.asText().strip();
            if (!name.isEmpty() && name.length() <= MAX_SKILL_NAME_LENGTH
                    && seen.add(name.toLowerCase(Locale.ROOT))) {
                skills.add(name);
            }
        }
        return skills;
    }

    private static String stripToJson(String text) {
        String candidate = text.strip();
        Matcher fence = FENCE.matcher(candidate);
        if (fence.find()) {
            candidate = fence.group(1).strip();
        }
        if (candidate.startsWith("{") || candidate.startsWith("[")) {
            return candidate;
        }
        // Texto suelto alrededor del JSON: nos quedamos con el primer bloque {...} o [...].
        int objStart = candidate.indexOf('{');
        int arrStart = candidate.indexOf('[');
        boolean useObject = objStart >= 0 && (arrStart < 0 || objStart < arrStart);
        int start = useObject ? objStart : arrStart;
        int end = candidate.lastIndexOf(useObject ? '}' : ']');
        if (start < 0 || end <= start) {
            throw new AiInvalidResponseException(INVALID_MESSAGE);
        }
        return candidate.substring(start, end + 1);
    }
}
