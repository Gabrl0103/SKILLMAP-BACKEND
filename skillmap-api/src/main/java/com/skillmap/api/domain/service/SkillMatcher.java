package com.skillmap.api.domain.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decide si un texto (una oferta de empleo) menciona una habilidad del catálogo.
 * Es dominio puro: solo expresiones regulares, sin IA ni librerías.
 *
 * Reglas generales:
 * - Se busca la palabra completa: "Java" no cuenta dentro de "JavaScript" ni "SQL" dentro de "NoSQL".
 * - Los términos de una o dos letras ("R", "ML") distinguen mayúsculas; el resto no, salvo que
 *   su regla diga lo contrario.
 * - Una sola letra además no puede ir seguida de "&" ni "/": "R" no cuenta en "R&D" ni en "R/3".
 * - Espacios y guiones son intercambiables: "CI CD" encuentra "CI-CD" y "end-to-end" encuentra "end to end".
 */
public final class SkillMatcher {

    /** Cuánto texto se mira a cada lado de una coincidencia para buscar su contexto. */
    static final int CONTEXT_WINDOW = 150;

    /**
     * Único lugar con las reglas especiales, por nombre del catálogo en minúsculas.
     * Una habilidad que aparece aquí se busca SOLO por estos términos (el nombre del catálogo
     * cuenta únicamente si está en la lista). Sirve para:
     * - nombres en español que no aparecen en ofertas en inglés ("Estadística");
     * - abreviaturas y variantes ("K8s", "ReactJS");
     * - nombres que son palabras comunes del inglés ("react", "swift", "testing"), que solo
     *   cuentan escritos como el producto o con el contexto adecuado.
     */
    private static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry("react", Rule.of(exact("React"), term("React.js"), term("ReactJS"))),
            Map.entry("swift", Rule.of(exact("Swift"))
                    .near("iOS", "Apple", "Xcode", "Kotlin", "mobile")),
            Map.entry("testing", Rule.of(term("unit testing"), term("test automation"), term("QA automation"),
                    exact("Jest"), exact("Cypress"), exact("Playwright"),
                    // "end-to-end" a secas es casi siempre frase de negocio ("end-to-end ownership").
                    term("end-to-end test"), term("end-to-end tests"), term("end-to-end testing"))),
            Map.entry("estadística", Rule.of(term("Estadística"), term("estadistica"),
                    term("statistics"), term("statistical"))),
            Map.entry("ci/cd", Rule.of(term("CI/CD"), term("CI CD"), term("CICD"),
                    term("continuous integration"))),
            Map.entry("kubernetes", Rule.of(term("Kubernetes"), term("K8s"))),
            Map.entry("next.js", Rule.of(term("Next.js"), term("NextJS"))),
            Map.entry("aws", Rule.of(term("AWS"), term("Amazon Web Services"))),
            Map.entry("tailwind css", Rule.of(term("Tailwind CSS"), term("Tailwind"), term("TailwindCSS"))),
            Map.entry("spring boot", Rule.of(term("Spring Boot"), term("SpringBoot"))),
            Map.entry("machine learning", Rule.of(term("Machine Learning"), exact("ML"))),
            Map.entry("sql", Rule.of(term("SQL"), term("PostgreSQL"), term("Postgres"), term("MySQL")))
    );

    /** Ni letra, ni dígito, ni los símbolos que forman parte de nombres como "C#", "C++" o "Node.js". */
    private static final String BEFORE = "(?<![\\p{L}\\p{N}_+#.])";
    private static final String AFTER = "(?![\\p{L}\\p{N}_+#])";
    private static final String SINGLE_LETTER_AFTER = "(?![\\p{L}\\p{N}_+#&/])";
    private static final int ANY_CASE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private final List<Pattern> patterns;
    /** Si no es null, cada coincidencia necesita esto a menos de CONTEXT_WINDOW caracteres. */
    private final Pattern context;

    private SkillMatcher(List<Pattern> patterns, Pattern context) {
        this.patterns = patterns;
        this.context = context;
    }

    /** Compila los patrones una vez; reutiliza el resultado para recorrer muchas ofertas. */
    public static SkillMatcher forSkill(String skillName) {
        String name = skillName.strip();
        Rule rule = RULES.getOrDefault(name.toLowerCase(Locale.ROOT), Rule.of(term(name)));
        return new SkillMatcher(rule.terms().stream().map(SkillMatcher::compile).toList(), rule.context());
    }

    public boolean matches(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (context == null || hasContext(text, matcher.start(), matcher.end())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasContext(String text, int start, int end) {
        String window = text.substring(Math.max(0, start - CONTEXT_WINDOW),
                Math.min(text.length(), end + CONTEXT_WINDOW));
        return context.matcher(window).find();
    }

    private static Pattern compile(Term term) {
        String body = Arrays.stream(term.text().split("[\\s-]+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("[\\s-]+"));
        if (term.text().length() == 1) {
            return Pattern.compile(BEFORE + body + SINGLE_LETTER_AFTER);
        }
        return Pattern.compile(BEFORE + body + AFTER, term.caseSensitive() ? 0 : ANY_CASE);
    }

    /** Distingue mayúsculas solo si es muy corto ("R", "ML"): ahí una minúscula suele ser otra cosa. */
    private static Term term(String text) {
        return new Term(text, text.length() <= 2);
    }

    /** Tal cual se escribe el producto: "React" sí, "react" (el verbo) no. */
    private static Term exact(String text) {
        return new Term(text, true);
    }

    private record Term(String text, boolean caseSensitive) {
    }

    private record Rule(List<Term> terms, Pattern context) {
        static Rule of(Term... terms) {
            return new Rule(List.of(terms), null);
        }

        Rule near(String... words) {
            String alternatives = Arrays.stream(words).map(Pattern::quote).collect(Collectors.joining("|"));
            return new Rule(terms, Pattern.compile(BEFORE + "(?:" + alternatives + ")" + AFTER, ANY_CASE));
        }
    }
}
