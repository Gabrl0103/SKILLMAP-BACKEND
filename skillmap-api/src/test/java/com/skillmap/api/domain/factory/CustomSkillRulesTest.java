package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.InvalidCustomGoalException;
import com.skillmap.api.domain.model.CustomGoal;
import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Habilidades propias de un objetivo (newSkills): nombre, duplicados con el catálogo y con el objetivo, y el total. */
class CustomSkillRulesTest {

    private static final Map<Long, String> CATALOG = Map.of(1L, "Python", 2L, "SQL", 3L, "Estadística", 4L, "Docker");
    private static final List<String> TAKEN = List.of("Frontend Senior");
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private static CustomGoalFactory.Draft create(List<Long> ids, List<String> newSkills) {
        return CustomGoalFactory.create(7L, "Chef", null, ids, newSkills, CATALOG, Map.of(), TAKEN, 0, NOW);
    }

    @Test
    void onlyNewSkillsAreEnoughAndKeepTheirOrder() {
        CustomGoalFactory.Draft draft = create(List.of(), List.of("  Cocina   francesa ", "Panadería", "Gestión de cocina"));
        assertThat(draft.goal().skillIds()).isEmpty();
        assertThat(draft.newSkills()).containsExactly("Cocina francesa", "Panadería", "Gestión de cocina");
    }

    @Test
    void mixedCatalogAndNewCountTogetherBetweenThreeAndEight() {
        assertThat(create(List.of(1L, 2L), List.of("Figma")).goal().skillIds()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> create(List.of(1L), List.of("Figma")))
                .isInstanceOf(InvalidCustomGoalException.class).hasMessage(CustomGoalFactory.SKILLS);
        assertThatThrownBy(() -> create(List.of(1L, 2L, 3L, 4L),
                List.of("A1", "B2", "C3", "D4", "E5"))).hasMessage(CustomGoalFactory.SKILLS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", " ", "Una habilidad con un nombre larguísimo de verdad"})
    void newSkillNameIsTwoToFortyCharacters(String name) {
        assertThatThrownBy(() -> create(List.of(1L, 2L), List.of(name)))
                .isInstanceOf(InvalidCustomGoalException.class).hasMessage(CustomGoalFactory.NEW_SKILL_NAME);
    }

    @Test
    void newSkillIsPlainText() {
        assertThatThrownBy(() -> create(List.of(1L, 2L), List.of("<b>Figma</b>")))
                .hasMessage(CustomGoalFactory.PLAIN);
    }

    @Test
    void newSkillCannotRepeatACatalogSkillIgnoringCase() {
        assertThatThrownBy(() -> create(List.of(1L, 2L), List.of("  sql ")))
                .hasMessage(CustomGoalFactory.IN_CATALOG);
    }

    @Test
    void newSkillsCannotRepeatEachOtherOrAKeptOwnSkill() {
        assertThatThrownBy(() -> create(List.of(1L), List.of("Figma", "FIGMA")))
                .hasMessage(CustomGoalFactory.REPEATED);
        CustomGoal saved = new CustomGoal(1_000_000L, 7L, "Diseño", null, List.of(1L, 2L, 50L), NOW, NOW);
        Map<Long, String> own = Map.of(50L, "Figma");
        assertThatThrownBy(() -> CustomGoalFactory.edit(saved, "Diseño", null, List.of(1L, 2L, 50L), List.of("figma"),
                CATALOG, own, TAKEN, NOW)).hasMessage(CustomGoalFactory.REPEATED);
        // Si se quita la propia, se puede volver a escribir.
        assertThat(CustomGoalFactory.edit(saved, "Diseño", null, List.of(1L, 2L), List.of("figma"), CATALOG, own,
                TAKEN, NOW).newSkills()).containsExactly("figma");
    }

    @Test
    void ownSkillIdsAreOnlyThoseOfThisGoal() {
        CustomGoal saved = new CustomGoal(1_000_000L, 7L, "Diseño", null, List.of(1L, 2L, 50L), NOW, NOW);
        // 60 es una propia de otro objetivo (o de otro usuario): no existe para este.
        assertThatThrownBy(() -> CustomGoalFactory.edit(saved, "Diseño", null, List.of(1L, 2L, 60L), List.of(),
                CATALOG, Map.of(50L, "Figma"), TAKEN, NOW)).hasMessage(CustomGoalFactory.UNKNOWN_SKILL);
    }

    @Test
    void customSkillHasNoDemandData() {
        Skill custom = Skill.custom(99L, "Figma", 7L, 1_000_000L);
        assertThat(custom.isCustom()).isTrue();
        assertThat(custom.hasDemandData()).isFalse();
        assertThat(custom.getCategory()).isEqualTo(Skill.CUSTOM_CATEGORY);
        assertThat(custom.getDemandPercentage()).isZero();
        assertThat(custom.getDemandSource()).isNull();
        assertThat(custom.isVisibleTo(7L)).isTrue();
        assertThat(custom.isVisibleTo(8L)).isFalse();
        assertThat(custom.withProgress(SkillStatus.MASTERED, NOW).isCustom()).isTrue();
        assertThatThrownBy(() -> custom.updateDemandFromJobPostings(40)).isInstanceOf(IllegalStateException.class);

        Skill catalog = SkillFactory.reconstruct(1L, "Python", "Datos", 60, SkillStatus.PENDING, DemandSource.SEEDED);
        assertThat(catalog.hasDemandData()).isTrue();
        assertThat(catalog.isVisibleTo(8L)).isTrue();
    }
}
