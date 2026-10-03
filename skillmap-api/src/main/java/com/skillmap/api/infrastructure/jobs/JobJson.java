package com.skillmap.api.infrastructure.jobs;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Utilidades para leer las respuestas JSON de las bolsas de empleo, que traen la descripción en HTML. */
final class JobJson {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<String, String> NAMED_ENTITIES = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
            Map.entry("rsquo", "'"), Map.entry("lsquo", "'"), Map.entry("rdquo", "\""),
            Map.entry("ldquo", "\""), Map.entry("ndash", "-"), Map.entry("mdash", "-"),
            Map.entry("hellip", "..."), Map.entry("bull", "-"));

    private JobJson() {
    }

    /** Texto del campo, o null si falta o es null. */
    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().strip();
        return text.isEmpty() ? null : text;
    }

    /** Etiquetas como texto separado por comas, o null si no hay. */
    static String tags(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            return null;
        }
        String tags = StreamSupport.stream(value.spliterator(), false)
                .map(JsonNode::asText)
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(", "));
        return tags.isEmpty() ? null : tags;
    }

    /**
     * HTML a texto plano. Se decodifican las entidades antes y después de quitar etiquetas
     * porque Arbeitnow manda el HTML escapado ("&amp;lt;p&amp;gt;").
     */
    static String plainText(String html) {
        if (html == null) {
            return null;
        }
        String text = decodeEntities(html);
        text = TAG.matcher(text).replaceAll(" ");
        text = decodeEntities(text);
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    private static String decodeEntities(String text) {
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(decode(matcher.group(1), matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String decode(String entity, String original) {
        if (entity.charAt(0) != '#') {
            return NAMED_ENTITIES.getOrDefault(entity.toLowerCase(), original);
        }
        boolean hex = entity.length() > 1 && (entity.charAt(1) == 'x' || entity.charAt(1) == 'X');
        int codePoint = Integer.parseInt(entity.substring(hex ? 2 : 1), hex ? 16 : 10);
        return Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : original;
    }
}
