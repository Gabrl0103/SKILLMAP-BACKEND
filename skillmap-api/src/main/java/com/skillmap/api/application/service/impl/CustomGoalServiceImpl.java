package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.CustomGoalService;
import com.skillmap.api.application.service.ReadinessHistoryService;
import com.skillmap.api.domain.exception.AuthenticationRequiredException;
import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.exception.GoalNotEditableException;
import com.skillmap.api.domain.factory.CustomGoalFactory;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.CustomGoal;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.domain.repository.CustomGoalRepository;
import com.skillmap.api.domain.repository.ReadinessSnapshotRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Objetivos propios: las reglas están en CustomGoalFactory (dominio); aquí se reúne lo que necesitan (catálogo,
 * nombres ocupados, cuántos tiene el usuario) y se cuida lo que cambia alrededor:
 * - Editar un objetivo base o borrarlo: 403. Uno propio de otro usuario: 404 (no existe para él).
 * - Editar registra la nueva preparación en el historial (las habilidades pudieron cambiar).
 * - Borrar quita también su historial y, si era el elegido, el usuario vuelve a su último objetivo base (o al primero).
 *   El progreso por habilidad es del usuario y no se toca.
 */
@Service
public class CustomGoalServiceImpl implements CustomGoalService {

    private final CustomGoalRepository customGoals;
    private final CareerGoalRepository baseGoals;
    private final SkillRepository skills;
    private final UserRepository users;
    private final ReadinessSnapshotRepository snapshots;
    private final ReadinessHistoryService readinessHistory;
    private final Clock clock;

    public CustomGoalServiceImpl(CustomGoalRepository customGoals, CareerGoalRepository baseGoals,
                                 SkillRepository skills, UserRepository users, ReadinessSnapshotRepository snapshots,
                                 ReadinessHistoryService readinessHistory, Clock clock) {
        this.customGoals = customGoals;
        this.baseGoals = baseGoals;
        this.skills = skills;
        this.users = users;
        this.snapshots = snapshots;
        this.readinessHistory = readinessHistory;
        this.clock = clock;
    }

    @Override
    public List<CustomGoal> list(Long userId) {
        return customGoals.findByUserId(userId);
    }

    @Override
    @Transactional
    public CustomGoal create(Long userId, String name, String description, List<Long> skillIds) {
        List<CustomGoal> own = customGoals.findByUserId(userId);
        CustomGoal goal = CustomGoalFactory.create(userId, name, description, skillIds, catalogIds(),
                takenNames(own, null), own.size(), clock.instant());
        return customGoals.save(goal);
    }

    @Override
    @Transactional
    public CustomGoal update(Long userId, Long id, String name, String description, List<Long> skillIds) {
        CustomGoal current = ownGoal(userId, id);
        CustomGoal edited = CustomGoalFactory.edit(current, name, description, skillIds, catalogIds(),
                takenNames(customGoals.findByUserId(userId), id), clock.instant());
        CustomGoal saved = customGoals.save(edited);
        readinessHistory.recordGoal(userId, saved.id());
        return saved;
    }

    @Override
    @Transactional
    public Long delete(Long userId, Long id) {
        ownGoal(userId, id);
        User user = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
        customGoals.delete(userId, id);
        snapshots.deleteByUserIdAndGoalId(userId, id);
        if (!id.equals(user.getGoalId())) {
            return user.getGoalId();
        }
        Long fallback = baseGoals.findById(Objects.requireNonNullElse(user.getBaseGoalId(), -1L))
                .or(() -> baseGoals.findAll().stream().findFirst())
                .map(CareerGoal::getId)
                .orElse(null);
        users.save(user.withGoal(fallback));
        if (fallback != null) {
            readinessHistory.recordGoal(userId, fallback);
        }
        return fallback;
    }

    /** El objetivo propio del usuario; 403 si es uno base y 404 si no existe o es de otro usuario. */
    private CustomGoal ownGoal(Long userId, Long id) {
        if (id != null && baseGoals.findById(id).isPresent()) {
            throw new GoalNotEditableException();
        }
        return customGoals.find(userId, id).orElseThrow(() -> new CareerGoalNotFoundException(id));
    }

    private Set<Long> catalogIds() {
        return skills.findAll().stream().map(Skill::getId).collect(Collectors.toSet());
    }

    /** Títulos de los objetivos base y nombres de los propios del usuario, menos el que se edita. */
    private List<String> takenNames(List<CustomGoal> own, Long editingId) {
        List<String> names = new ArrayList<>(baseGoals.findAll().stream().map(CareerGoal::getTitle).toList());
        own.stream().filter(g -> !g.id().equals(editingId)).map(CustomGoal::name).forEach(names::add);
        return names;
    }
}
