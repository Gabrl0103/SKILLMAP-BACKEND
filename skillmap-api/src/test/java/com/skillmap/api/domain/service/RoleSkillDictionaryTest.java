package com.skillmap.api.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSkillDictionaryTest {

    @Test
    void everyProfileHasFiveToEightDistinctSkillsAndKeywords() {
        Set<String> ids = new HashSet<>();
        for (RoleSkillDictionary.Profile profile : RoleSkillDictionary.profiles()) {
            assertThat(ids.add(profile.id())).as("id repetido: %s", profile.id()).isTrue();
            assertThat(profile.keywords()).isNotEmpty();
            assertThat(profile.skills()).hasSizeBetween(5, 8).doesNotHaveDuplicates();
            // Cada nombre debe valer como habilidad propia (2 a 40 caracteres, sin < >).
            profile.skills().forEach(skill -> assertThat(skill.length()).isBetween(1, 40));
            profile.skills().forEach(skill -> assertThat(skill).doesNotContain("<", ">"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "Backend Python, backend-python",
            "Desarrollador Backend, backend",
            "backend java senior, backend-java",
            "Frontend Senior, frontend",
            "Full-Stack Developer, fullstack",
            "Diseñador UX, ux",
            "DISEÑADORA UX/UI, ux",
            "UI Designer, ui",
            "Diseñador gráfico, graphic",
            "Data Analyst, data-analyst",
            "Científico de datos, data-scientist",
            "Ingeniero de datos, data-engineer",
            "Ingeniero de Machine Learning, ml-engineer",
            "QA Engineer, qa",
            "DevOps, devops",
            "Ciberseguridad, security",
            "Product Owner, product",
            "Scrum Master, project",
            "Marketing Digital, marketing",
            "Especialista SEO, seo",
            "Community Manager, content",
            "Soporte técnico, support",
            "Administrador de sistemas, sysadmin",
            "Desarrollador Android, android",
            "Node.js developer, backend-node"
    })
    void matchesCommonRolesInSpanishAndEnglish(String role, String profile) {
        assertThat(RoleSkillDictionary.find(role)).get()
                .extracting(match -> match.profile().id())
                .isEqualTo(profile);
        assertThat(RoleSkillDictionary.suggest(role)).hasSizeBetween(5, 8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Chef", "Enfermera", "Piloto de avión", "Luxemburgo", "Guía turístico", "", "  "})
    void unrelatedRolesHaveNoSuggestions(String role) {
        assertThat(RoleSkillDictionary.find(role)).isEmpty();
        assertThat(RoleSkillDictionary.suggest(role)).isEmpty();
    }

    @Test
    void theMostSpecificKeywordWins() {
        // "backend" y "backend python" coinciden: gana la más larga.
        assertThat(RoleSkillDictionary.suggest("Backend Python")).startsWith("Python");
        assertThat(RoleSkillDictionary.find("Diseñador UX").orElseThrow().keyword()).isEqualTo("disenador ux");
    }

    @Test
    void uxSuggestionsAreDesignSkillsNotTechnology() {
        List<String> ux = RoleSkillDictionary.suggest("Diseñador UX");
        assertThat(ux).contains("Figma", "Investigación de usuarios", "Prototipado")
                .doesNotContain("Java", "Docker", "SQL");
    }

    @Test
    void normalizeIgnoresCaseAccentsAndPunctuation() {
        assertThat(RoleSkillDictionary.normalize("  Diseñador   UX/UI ")).isEqualTo("disenador ux ui");
        assertThat(RoleSkillDictionary.normalize("Estadística")).isEqualTo("estadistica");
        assertThat(RoleSkillDictionary.normalize("C#")).isEqualTo("c#");
        assertThat(RoleSkillDictionary.normalize(null)).isEmpty();
    }
}
