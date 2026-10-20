package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.CareerGoalService;
import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.exception.InvalidHistoryRangeException;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalReadiness;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.domain.model.ReadinessSnapshot;
import com.skillmap.api.domain.repository.ReadinessSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Reglas del historial de preparación con un reloj que avanza a mano y un repositorio en memoria. */
class ReadinessHistoryServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    // Mediodía en la zona del equipo: sumar unas horas no cambia de día.
    private static final Instant NOON = DAY.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;
    private static final long GOAL = 10L;
    private static final long OTHER_GOAL = 20L;

    private final MutableClock clock = new MutableClock(NOON);
    private final InMemorySnapshots snapshots = new InMemorySnapshots();
    private final FakeGoals goals = new FakeGoals();
    private ReadinessHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReadinessHistoryServiceImpl(goals, snapshots, clock);
    }

    @Test
    void firstChangeStoresTheCurrentPercentage() {
        goals.readiness.put(GOAL, 25);

        service.recordGoal(USER, GOAL);

        assertThat(snapshots.all()).singleElement().satisfies(s -> {
            assertThat(s.userId()).isEqualTo(USER);
            assertThat(s.goalId()).isEqualTo(GOAL);
            assertThat(s.percentage()).isEqualTo(25);
            assertThat(s.capturedOn()).isEqualTo(DAY);
            assertThat(s.capturedAt()).isEqualTo(NOON);
        });
    }

    @Test
    void sameDayKeepsOneSnapshotWithTheLatestValue() {
        goals.readiness.put(GOAL, 25);
        service.recordGoal(USER, GOAL);
        clock.advance(Duration.ofHours(2));
        goals.readiness.put(GOAL, 50);
        service.recordGoal(USER, GOAL);
        clock.advance(Duration.ofHours(1));
        goals.readiness.put(GOAL, 40);
        service.recordGoal(USER, GOAL);

        assertThat(snapshots.all()).singleElement().satisfies(s -> {
            assertThat(s.percentage()).isEqualTo(40);
            assertThat(s.capturedOn()).isEqualTo(DAY);
            assertThat(s.capturedAt()).isEqualTo(NOON.plus(Duration.ofHours(3)));
        });
    }

    @Test
    void unchangedPercentageSavesNothingNewEvenOnAnotherDay() {
        goals.readiness.put(GOAL, 25);
        service.recordGoal(USER, GOAL);
        clock.advance(Duration.ofHours(1));
        service.recordGoal(USER, GOAL);
        clock.advance(Duration.ofDays(3));
        service.recordGoal(USER, GOAL);

        assertThat(snapshots.all()).singleElement().satisfies(s -> {
            assertThat(s.capturedOn()).isEqualTo(DAY);
            assertThat(s.capturedAt()).isEqualTo(NOON);
        });
    }

    @Test
    void aChangeOnANewDayAddsAPoint() {
        goals.readiness.put(GOAL, 25);
        service.recordGoal(USER, GOAL);
        clock.advance(Duration.ofDays(1));
        goals.readiness.put(GOAL, 50);
        service.recordGoal(USER, GOAL);

        assertThat(service.getHistory(USER, GOAL, 30))
                .extracting(ReadinessSnapshot::capturedOn, ReadinessSnapshot::percentage)
                .containsExactly(tuple(DAY, 25), tuple(DAY.plusDays(1), 50));
    }

    @Test
    void skillChangeRecordsOnlyTheGoalsThatIncludeTheSkill() {
        goals.goals.add(goal(GOAL, 100L, 101L));
        goals.goals.add(goal(OTHER_GOAL, 200L));
        goals.readiness.put(GOAL, 60);
        goals.readiness.put(OTHER_GOAL, 0);

        service.recordSkillChange(USER, 101L);

        assertThat(snapshots.all()).singleElement().satisfies(s -> {
            assertThat(s.goalId()).isEqualTo(GOAL);
            assertThat(s.percentage()).isEqualTo(60);
        });
    }

    @Test
    void historyIsOrderedByDateAndLimitedToTheRequestedDays() {
        // Guardados fuera de orden: 10 días atrás, hoy, 2 días atrás y 40 días atrás.
        snapshots.save(new ReadinessSnapshot(null, USER, GOAL, 30, DAY.minusDays(10), NOON));
        snapshots.save(new ReadinessSnapshot(null, USER, GOAL, 60, DAY, NOON));
        snapshots.save(new ReadinessSnapshot(null, USER, GOAL, 45, DAY.minusDays(2), NOON));
        snapshots.save(new ReadinessSnapshot(null, USER, GOAL, 10, DAY.minusDays(40), NOON));
        goals.readiness.put(GOAL, 60);

        assertThat(service.getHistory(USER, GOAL, 30)).extracting(ReadinessSnapshot::percentage)
                .containsExactly(30, 45, 60);
        assertThat(service.getHistory(USER, GOAL, 3)).extracting(ReadinessSnapshot::percentage)
                .containsExactly(45, 60);
        assertThat(service.getHistory(USER, GOAL, 1)).extracting(ReadinessSnapshot::percentage)
                .containsExactly(60);
        assertThat(service.getHistory(USER, GOAL, 365)).extracting(ReadinessSnapshot::percentage)
                .containsExactly(10, 30, 45, 60);
    }

    @Test
    void historyWithoutSnapshotsStoresTheCurrentStateAsStartingPoint() {
        goals.readiness.put(GOAL, 35);

        assertThat(service.getHistory(USER, GOAL, 30)).extracting(ReadinessSnapshot::percentage).containsExactly(35);
        // Ya hay historial: pedirlo otra vez no agrega nada aunque el % haya cambiado por otra vía.
        goals.readiness.put(GOAL, 70);
        clock.advance(Duration.ofDays(1));
        assertThat(service.getHistory(USER, GOAL, 30)).extracting(ReadinessSnapshot::percentage).containsExactly(35);
        assertThat(snapshots.all()).hasSize(1);
    }

    @Test
    void historyIsPerUserAndGoal() {
        goals.readiness.put(GOAL, 25);
        goals.readiness.put(OTHER_GOAL, 80);
        service.recordGoal(USER, GOAL);
        service.recordGoal(OTHER_USER, GOAL);
        service.recordGoal(USER, OTHER_GOAL);

        assertThat(service.getHistory(USER, GOAL, 30)).singleElement()
                .satisfies(s -> assertThat(s.userId()).isEqualTo(USER));
        assertThat(service.getHistory(USER, OTHER_GOAL, 30)).singleElement()
                .satisfies(s -> assertThat(s.percentage()).isEqualTo(80));
    }

    @Test
    void daysOutOfRangeAreRejected() {
        goals.readiness.put(GOAL, 25);
        for (int days : new int[] {0, -1, 366}) {
            assertThatThrownBy(() -> service.getHistory(USER, GOAL, days))
                    .isInstanceOf(InvalidHistoryRangeException.class)
                    .hasMessage("El parámetro 'days' debe estar entre 1 y 365.");
        }
        assertThat(snapshots.all()).isEmpty();
    }

    @Test
    void unknownGoalIsNotFoundAndStoresNothing() {
        assertThatThrownBy(() -> service.getHistory(USER, 999L, 30)).isInstanceOf(CareerGoalNotFoundException.class);
        assertThat(snapshots.all()).isEmpty();
    }

    private static CareerGoal goal(long id, long... skillIds) {
        List<GoalSkill> skills = new ArrayList<>();
        for (long skillId : skillIds) {
            skills.add(new GoalSkill(skillId, 3));
        }
        return new CareerGoal(id, "Objetivo " + id, "", skills);
    }

    /** Objetivos con un % fijo cada uno; uno sin % no existe. */
    private static final class FakeGoals implements CareerGoalService {
        final List<CareerGoal> goals = new ArrayList<>();
        final Map<Long, Integer> readiness = new HashMap<>();

        @Override
        public List<CareerGoal> getAllGoals() {
            return goals;
        }

        @Override
        public CareerGoal getGoalById(Long id) {
            if (!readiness.containsKey(id)) {
                throw new CareerGoalNotFoundException(id);
            }
            return goals.stream().filter(g -> g.getId().equals(id)).findFirst().orElseGet(() -> goal(id));
        }

        @Override
        public GoalReadiness getReadiness(Long userId, Long goalId) {
            return new GoalReadiness(getGoalById(goalId), readiness.get(goalId), 0, 0, List.of());
        }
    }

    private static final class InMemorySnapshots implements ReadinessSnapshotRepository {
        private final List<ReadinessSnapshot> rows = new ArrayList<>();
        private long nextId = 1;

        List<ReadinessSnapshot> all() {
            return List.copyOf(rows);
        }

        @Override
        public Optional<ReadinessSnapshot> findLatest(Long userId, Long goalId) {
            return rowsOf(userId, goalId).max(Comparator.comparing(ReadinessSnapshot::capturedOn));
        }

        @Override
        public List<ReadinessSnapshot> findSince(Long userId, Long goalId, LocalDate from) {
            return rowsOf(userId, goalId).filter(s -> !s.capturedOn().isBefore(from))
                    .sorted(Comparator.comparing(ReadinessSnapshot::capturedOn)).toList();
        }

        @Override
        public ReadinessSnapshot save(ReadinessSnapshot s) {
            ReadinessSnapshot saved = s.id() != null ? s
                    : new ReadinessSnapshot(nextId++, s.userId(), s.goalId(), s.percentage(), s.capturedOn(), s.capturedAt());
            rows.removeIf(r -> r.id().equals(saved.id()));
            rows.add(saved);
            return saved;
        }

        @Override
        public void deleteByUserId(Long userId) {
            rows.removeIf(r -> r.userId().equals(userId));
        }

        private Stream<ReadinessSnapshot> rowsOf(Long userId, Long goalId) {
            return rows.stream().filter(r -> r.userId().equals(userId) && r.goalId().equals(goalId));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
