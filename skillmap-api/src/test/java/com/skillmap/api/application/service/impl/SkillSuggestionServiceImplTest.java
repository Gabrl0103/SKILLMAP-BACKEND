package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.InvalidCustomGoalException;
import com.skillmap.api.domain.factory.SkillFactory;
import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.SkillSuggestions;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.SkillRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSuggestionServiceImplTest {

    private static final List<Skill> CATALOG = List.of(
            SkillFactory.reconstruct(8L, "SQL", "Datos", 50, SkillStatus.PENDING, DemandSource.SEEDED),
            SkillFactory.reconstruct(10L, "Python", "Datos", 60, SkillStatus.PENDING, DemandSource.SEEDED),
            SkillFactory.reconstruct(13L, "Estadística", "Datos", 40, SkillStatus.PENDING, DemandSource.SEEDED),
            SkillFactory.reconstruct(9L, "Docker", "DevOps", 40, SkillStatus.PENDING, DemandSource.SEEDED));

    private final AtomicInteger aiCalls = new AtomicInteger();
    private SkillSuggestionServiceImpl service;

    @AfterEach
    void close() {
        if (service != null) {
            service.close();
        }
    }

    private SkillSuggestionServiceImpl service(JobSkillExtractor extractor, Duration timeout) {
        service = new SkillSuggestionServiceImpl(new FakeSkills(), (text, known) -> {
            aiCalls.incrementAndGet();
            return extractor.extractSkills(text, known);
        }, timeout);
        return service;
    }

    @Test
    void dictionaryRolesUseCatalogIdsAndOfferTheRestAsNewWithoutCallingTheAi() {
        SkillSuggestions result = service((t, k) -> List.of("Nunca"), Duration.ofSeconds(1))
                .suggest(1L, "Backend Python");
        assertThat(result.source()).isEqualTo(SkillSuggestions.Source.DICTIONARY);
        assertThat(result.skills()).hasSizeBetween(5, 8);
        assertThat(result.skills().get(0)).isEqualTo(new SkillSuggestions.Item(10L, "Python", "Datos"));
        assertThat(result.skills()).anySatisfy(item -> {
            assertThat(item.name()).isEqualTo("Django");
            assertThat(item.isNew()).isTrue();
            assertThat(item.category()).isEqualTo(Skill.CUSTOM_CATEGORY);
        });
        assertThat(aiCalls).hasValue(0);
    }

    @Test
    void unknownRoleFallsBackToTheAiAndMatchesTheCatalogIgnoringAccents() {
        SkillSuggestions result = service((t, k) -> List.of("estadistica", "Cocina francesa", "Cocina francesa",
                "Gestión de inventario", "x", "<script>"), Duration.ofSeconds(2)).suggest(1L, "Chef");
        assertThat(result.source()).isEqualTo(SkillSuggestions.Source.AI);
        assertThat(result.skills()).extracting(SkillSuggestions.Item::name)
                .containsExactly("Estadística", "Cocina francesa", "Gestión de inventario");
        assertThat(result.skills().get(0).skillId()).isEqualTo(13L);
        assertThat(aiCalls).hasValue(1);
    }

    @Test
    void anAiFailureOrNoAnswerEndsWithoutSuggestions() {
        assertThat(service((t, k) -> { throw new IllegalStateException("sin red"); }, Duration.ofSeconds(1))
                .suggest(1L, "Chef")).isEqualTo(SkillSuggestions.none());
        assertThat(service((t, k) -> List.of(), Duration.ofSeconds(1)).suggest(1L, "Chef").source())
                .isEqualTo(SkillSuggestions.Source.NONE);
    }

    @Test
    void aSlowAiIsCutAtTheTimeout() {
        long start = System.nanoTime();
        SkillSuggestions result = service((t, k) -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of("Cocina");
        }, Duration.ofMillis(200)).suggest(1L, "Chef");
        assertThat(result.source()).isEqualTo(SkillSuggestions.Source.NONE);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void atMostEightSuggestions() {
        List<String> many = List.of("Uno1", "Dos2", "Tres3", "Cuatro4", "Cinco5", "Seis6", "Siete7", "Ocho8", "Nueve9");
        assertThat(service((t, k) -> many, Duration.ofSeconds(1)).suggest(1L, "Chef").skills()).hasSize(8);
    }

    @Test
    void theRoleNameIsValidated() {
        SkillSuggestionServiceImpl s = service((t, k) -> List.of(), Duration.ofSeconds(1));
        assertThatThrownBy(() -> s.suggest(1L, " ")).isInstanceOf(InvalidCustomGoalException.class);
        assertThatThrownBy(() -> s.suggest(1L, "<b>Chef</b>")).isInstanceOf(InvalidCustomGoalException.class);
        assertThatThrownBy(() -> s.suggest(1L, "x".repeat(41))).isInstanceOf(InvalidCustomGoalException.class);
    }

    /** Solo findAll (el catálogo) importa aquí. */
    private static final class FakeSkills implements SkillRepository {
        @Override public List<Skill> findAll() { return CATALOG; }
        @Override public Optional<Skill> findById(Long id) { return Optional.empty(); }
        @Override public Skill save(Skill skill) { return skill; }
        @Override public List<Skill> findByOwner(Long ownerId) { return List.of(); }
        @Override public void deleteOwned(Long ownerId, Collection<Long> ids) { }
        @Override public void deleteByOwner(Long ownerId) { }
    }
}
