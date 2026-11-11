package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.CustomGoalConflictException;
import com.skillmap.api.domain.exception.InvalidCustomGoalException;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.CustomGoal;
import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de los objetivos propios: nombre, descripción, habilidades, duplicados y máximo por usuario. */
class CustomGoalFactoryTest {

    private static final Set<Long> CATALOG = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    private static final List<String> TAKEN = List.of("Frontend Senior", "Backend Java", "Mi Rol QA");
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private static CustomGoal create(String name, String description, List<Long> skills) {
        return CustomGoalFactory.create(7L, name, description, skills, CATALOG, TAKEN, 0, NOW);
    }

    @Test
    void createsCleanGoalWithDates() {
        CustomGoal goal = create("  Ingeniero   de  Datos ", "  ETL y   pipelines ", List.of(3L, 1L, 2L));
        assertThat(goal.id()).isNull();
        assertThat(goal.userId()).isEqualTo(7L);
        assertThat(goal.name()).isEqualTo("Ingeniero de Datos");
        assertThat(goal.description()).isEqualTo("ETL y pipelines");
        assertThat(goal.skillIds()).containsExactly(3L, 1L, 2L);
        assertThat(goal.createdAt()).isEqualTo(NOW);
        assertThat(goal.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void blankDescriptionIsNull() {
        assertThat(create("QA Lead", "   ", List.of(1L, 2L, 3L)).description()).isNull();
        assertThat(create("QA Lead", null, List.of(1L, 2L, 3L)).description()).isNull();
    }

    @Test
    void nameLengthIsTwoToFortyCharacters() {
        assertThatThrownBy(() -> create("A", null, List.of(1L, 2L, 3L))).isInstanceOf(InvalidCustomGoalException.class)
                .hasMessage(CustomGoalFactory.NAME);
        assertThatThrownBy(() -> create("   ", null, List.of(1L, 2L, 3L))).isInstanceOf(InvalidCustomGoalException.class);
        assertThatThrownBy(() -> create(null, null, List.of(1L, 2L, 3L))).isInstanceOf(InvalidCustomGoalException.class);
        assertThatThrownBy(() -> create("x".repeat(41), null, List.of(1L, 2L, 3L)))
                .isInstanceOf(InvalidCustomGoalException.class);
        assertThat(create("Ab", null, List.of(1L, 2L, 3L)).name()).isEqualTo("Ab");
        assertThat(create("x".repeat(40), null, List.of(1L, 2L, 3L)).name()).hasSize(40);
        // Un emoji cuenta como un carácter
        assertThat(create("🚀".repeat(40), null, List.of(1L, 2L, 3L)).name()).isNotBlank();
    }

    @Test
    void onlyPlainText() {
        assertThatThrownBy(() -> create("<b>Rol</b>", null, List.of(1L, 2L, 3L)))
                .isInstanceOf(InvalidCustomGoalException.class).hasMessage(CustomGoalFactory.PLAIN);
        assertThatThrownBy(() -> create("Rol", "uno <script>", List.of(1L, 2L, 3L)))
                .isInstanceOf(InvalidCustomGoalException.class).hasMessage(CustomGoalFactory.PLAIN);
        assertThatThrownBy(() -> create("Rol\u0000x", null, List.of(1L, 2L, 3L)))
                .isInstanceOf(InvalidCustomGoalException.class);
    }

    @Test
    void descriptionUpTo120Characters() {
        assertThat(create("Rol", "d".repeat(120), List.of(1L, 2L, 3L)).description()).hasSize(120);
        assertThatThrownBy(() -> create("Rol", "d".repeat(121), List.of(1L, 2L, 3L)))
                .isInstanceOf(InvalidCustomGoalException.class).hasMessage(CustomGoalFactory.DESCRIPTION);
    }

    @Test
    void threeToEightSkillsOfTheCatalogWithoutRepeats() {
        assertThatThrownBy(() -> create("Rol", null, List.of(1L, 2L))).hasMessage(CustomGoalFactory.SKILLS);
        assertThatThrownBy(() -> create("Rol", null, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)))
                .hasMessage(CustomGoalFactory.SKILLS);
        assertThatThrownBy(() -> create("Rol", null, null)).hasMessage(CustomGoalFactory.SKILLS);
        assertThatThrownBy(() -> create("Rol", null, List.of(1L, 2L, 2L))).hasMessage(CustomGoalFactory.REPEATED);
        assertThatThrownBy(() -> create("Rol", null, List.of(1L, 2L, 999L))).hasMessage(CustomGoalFactory.UNKNOWN_SKILL);
        assertThat(create("Rol", null, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)).skillIds()).hasSize(8);
    }

    @Test
    void nameCannotRepeatABaseOrOwnGoalIgnoringCaseAndSpaces() {
        assertThatThrownBy(() -> create("frontend   SENIOR", null, List.of(1L, 2L, 3L)))
                .isInstanceOf(CustomGoalConflictException.class).hasMessage(CustomGoalFactory.DUPLICATE);
        assertThatThrownBy(() -> create(" mi rol qa ", null, List.of(1L, 2L, 3L)))
                .isInstanceOf(CustomGoalConflictException.class);
        assertThat(create("Frontend Senior 2", null, List.of(1L, 2L, 3L)).name()).isEqualTo("Frontend Senior 2");
    }

    @Test
    void atMostThreePerUser() {
        assertThat(CustomGoalFactory.create(7L, "Rol", null, List.of(1L, 2L, 3L), CATALOG, TAKEN, 2, NOW)).isNotNull();
        assertThatThrownBy(() -> CustomGoalFactory.create(7L, "Rol", null, List.of(1L, 2L, 3L), CATALOG, TAKEN, 3, NOW))
                .isInstanceOf(CustomGoalConflictException.class).hasMessage(CustomGoalFactory.LIMIT);
    }

    @Test
    void editKeepsIdOwnerAndCreationDate() {
        CustomGoal saved = new CustomGoal(1_000_000L, 7L, "Rol", null, List.of(1L, 2L, 3L), NOW, NOW);
        Instant later = NOW.plusSeconds(60);
        CustomGoal edited = CustomGoalFactory.edit(saved, "Rol nuevo", "algo", List.of(4L, 5L, 6L, 7L), CATALOG,
                TAKEN, later);
        assertThat(edited.id()).isEqualTo(1_000_000L);
        assertThat(edited.userId()).isEqualTo(7L);
        assertThat(edited.createdAt()).isEqualTo(NOW);
        assertThat(edited.updatedAt()).isEqualTo(later);
        assertThat(edited.skillIds()).containsExactly(4L, 5L, 6L, 7L);
        assertThatThrownBy(() -> CustomGoalFactory.edit(saved, "Backend java", null, List.of(1L, 2L, 3L), CATALOG,
                TAKEN, later)).isInstanceOf(CustomGoalConflictException.class);
    }

    @Test
    void readinessUsesTheSameRuleWithEqualWeights() {
        CareerGoal goal = new CustomGoal(1_000_000L, 7L, "Rol", null, List.of(1L, 2L, 3L, 4L), NOW, NOW).toCareerGoal();
        assertThat(goal.isCustom()).isTrue();
        assertThat(goal.getOwnerId()).isEqualTo(7L);
        assertThat(goal.getSkills()).extracting(GoalSkill::importance).containsOnly(CustomGoal.SKILL_IMPORTANCE);
        List<Skill> progress = List.of(skill(1L, SkillStatus.MASTERED), skill(2L, SkillStatus.IN_PROGRESS),
                skill(3L, SkillStatus.PENDING), skill(4L, SkillStatus.PENDING));
        assertThat(goal.calculateReadiness(progress)).isEqualTo(25);
    }

    private static Skill skill(long id, SkillStatus status) {
        return SkillFactory.reconstruct(id, "Skill " + id, "Cat", 50, status, DemandSource.SEEDED);
    }
}
