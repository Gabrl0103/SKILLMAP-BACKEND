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
import com.skillmap.api.domain.repository.UserSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Objetivos propios: las reglas están en CustomGoalFactory (dominio); aquí se reúne lo que necesitan (catálogo,
 * nombres ocupados, cuántos tiene el usuario) y se cuida lo que cambia alrededor:
 * - Editar un objetivo base o borrarlo: 403. Uno propio de otro usuario: 404 (no existe para él).
 * - Editar registra la nueva preparación en el historial (las habilidades pudieron cambiar).
 * - Borrar quita también su historial y, si era el elegido, el usuario vuelve a su último objetivo base (o al primero).
 *   El progreso en las habilidades del catálogo es del usuario y no se toca.
 * - Habilidades propias (las que escribe el usuario): son de un solo objetivo. Se crean al guardarlo y se borran, con
 *   su progreso, cuando se quitan de él o cuando se borra el objetivo.
 */
@Service
public class CustomGoalServiceImpl implements CustomGoalService {

    private final CustomGoalRepository customGoals;
    private final CareerGoalRepository baseGoals;
    private final SkillRepository skills;
    private final UserRepository users;
    private final ReadinessSnapshotRepository snapshots;
    private final ReadinessHistoryService readinessHistory;
    private final UserSkillRepository progress;
    private final Clock clock;

    public CustomGoalServiceImpl(CustomGoalRepository customGoals, CareerGoalRepository baseGoals,
                                 SkillRepository skills, UserRepository users, ReadinessSnapshotRepository snapshots,
                                 ReadinessHistoryService readinessHistory, UserSkillRepository progress, Clock clock) {
        this.customGoals = customGoals;
        this.baseGoals = baseGoals;
        this.skills = skills;
        this.users = users;
        this.snapshots = snapshots;
        this.readinessHistory = readinessHistory;
        this.progress = progress;
        this.clock = clock;
    }

    @Override
    public List<CustomGoal> list(Long userId) {
        return customGoals.findByUserId(userId);
    }

    @Override
    public CustomGoal create(Long userId, String name, String description, List<Long> skillIds) {
        return create(userId, name, description, skillIds, List.of());
    }

    @Override
    public CustomGoal update(Long userId, Long id, String name, String description, List<Long> skillIds) {
        return update(userId, id, name, description, skillIds, List.of());
    }

    @Override
    @Transactional
    public CustomGoal create(Long userId, String name, String description, List<Long> skillIds,
                             List<String> newSkills) {
        List<CustomGoal> own = customGoals.findByUserId(userId);
        CustomGoalFactory.Draft draft = CustomGoalFactory.create(userId, name, description, skillIds, newSkills,
                catalog(), Map.of(), takenNames(own, null), own.size(), clock.instant());
        CustomGoal saved = customGoals.save(draft.goal());
        return withNewSkills(saved, draft.newSkills());
    }

    @Override
    @Transactional
    public CustomGoal update(Long userId, Long id, String name, String description, List<Long> skillIds,
                             List<String> newSkills) {
        CustomGoal current = ownGoal(userId, id);
        Map<Long, String> ownOfGoal = ownSkillsOf(userId, id);
        CustomGoalFactory.Draft draft = CustomGoalFactory.edit(current, name, description, skillIds, newSkills,
                catalog(), ownOfGoal, takenNames(customGoals.findByUserId(userId), id), clock.instant());
        CustomGoal saved = withNewSkills(draft.goal(), draft.newSkills());
        // Las propias que ya no están en el objetivo se borran con su progreso (no sirven a ningún otro).
        List<Long> removed = ownOfGoal.keySet().stream().filter(skillId -> !saved.skillIds().contains(skillId)).toList();
        forgetOwnSkills(userId, removed);
        readinessHistory.recordGoal(userId, saved.id());
        return saved;
    }

    /** Guarda el objetivo con sus habilidades nuevas creadas al final de la lista (necesitan el id del objetivo). */
    private CustomGoal withNewSkills(CustomGoal goal, List<String> newSkills) {
        CustomGoal saved = customGoals.save(goal);
        if (newSkills.isEmpty()) {
            return saved;
        }
        List<Long> ids = new ArrayList<>(saved.skillIds());
        for (String skillName : newSkills) {
            ids.add(skills.save(Skill.custom(null, skillName, saved.userId(), saved.id())).getId());
        }
        return customGoals.save(saved.withChanges(saved.name(), saved.description(), ids, saved.updatedAt()));
    }

    /** id → nombre de las habilidades propias que el usuario tiene en ese objetivo. */
    private Map<Long, String> ownSkillsOf(Long userId, Long goalId) {
        Map<Long, String> own = new LinkedHashMap<>();
        skills.findByOwner(userId).stream()
                .filter(skill -> goalId.equals(skill.getGoalId()))
                .forEach(skill -> own.put(skill.getId(), skill.getName()));
        return own;
    }

    private void forgetOwnSkills(Long userId, List<Long> skillIds) {
        skillIds.forEach(skillId -> progress.delete(userId, skillId));
        skills.deleteOwned(userId, skillIds);
    }

    @Override
    @Transactional
    public Long delete(Long userId, Long id) {
        ownGoal(userId, id);
        User user = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
        List<Long> ownSkills = List.copyOf(ownSkillsOf(userId, id).keySet());
        customGoals.delete(userId, id);
        forgetOwnSkills(userId, ownSkills);
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

    /** id → nombre de todo el catálogo global. */
    private Map<Long, String> catalog() {
        Map<Long, String> catalog = new LinkedHashMap<>();
        skills.findAll().forEach(skill -> catalog.put(skill.getId(), skill.getName()));
        return catalog;
    }

    /** Títulos de los objetivos base y nombres de los propios del usuario, menos el que se edita. */
    private List<String> takenNames(List<CustomGoal> own, Long editingId) {
        List<String> names = new ArrayList<>(baseGoals.findAll().stream().map(CareerGoal::getTitle).toList());
        own.stream().filter(g -> !g.id().equals(editingId)).map(CustomGoal::name).forEach(names::add);
        return names;
    }
}
