package com.skillmap.api.infrastructure.jobs;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decide si una oferta de una bolsa generalista es técnica mirando su título y sus etiquetas.
 * Cada palabra se busca al inicio de una palabra y sin distinguir mayúsculas: "engineer"
 * encuentra "Engineering" y "software" encuentra "Softwareentwickler". Las de tres letras o
 * menos ("qa", "ios") tienen que ser la palabra entera, para no cazar "Qatar".
 *
 * Orden de las reglas:
 * 1. Si el título contiene una palabra excluida ("mechanical", "hausmeister"...), no es técnica.
 * 2. Es técnica si el título o las etiquetas contienen una palabra clave general, o si el
 *    título contiene una palabra clave solo-título ("engineer", "data": en las etiquetas son
 *    demasiado amplias, por ejemplo "Building Services Engineering").
 * 3. Sin palabras clave (ni generales ni solo-título) no se filtra nada, salvo las exclusiones.
 */
final class TechJobFilter {

    private static final int ANY_CASE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private final List<Pattern> keywords;
    private final List<Pattern> titleOnlyKeywords;
    private final List<Pattern> excludedTitleKeywords;

    TechJobFilter(List<String> keywords, List<String> titleOnlyKeywords, List<String> excludedTitleKeywords) {
        this.keywords = compileAll(keywords);
        this.titleOnlyKeywords = compileAll(titleOnlyKeywords);
        this.excludedTitleKeywords = compileAll(excludedTitleKeywords);
    }

    boolean isTechnical(String title, String tags) {
        String safeTitle = title == null ? "" : title;
        if (anyMatch(excludedTitleKeywords, safeTitle)) {
            return false;
        }
        if (keywords.isEmpty() && titleOnlyKeywords.isEmpty()) {
            return true;
        }
        return anyMatch(titleOnlyKeywords, safeTitle)
                || anyMatch(keywords, safeTitle + "\n" + (tags == null ? "" : tags));
    }

    private static boolean anyMatch(List<Pattern> patterns, String text) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }

    private static List<Pattern> compileAll(List<String> words) {
        return words.stream()
                .map(String::strip)
                .filter(w -> !w.isEmpty())
                .map(TechJobFilter::compile)
                .toList();
    }

    private static Pattern compile(String keyword) {
        String body = Arrays.stream(keyword.split("[\\s-]+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("[\\s-]+"));
        String end = keyword.length() <= 3 ? "(?![\\p{L}\\p{N}])" : "";
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + body + end, ANY_CASE);
    }
}
