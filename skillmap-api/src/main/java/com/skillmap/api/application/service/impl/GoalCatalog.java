package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.CustomGoal;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.domain.repository.CustomGoalRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Los objetivos que puede ver un usuario: los base y sus propios objetivos, ya como CareerGoal (mismas reglas para
 * todos). Única puerta para resolver un goalId en la aplicación (readiness, historial, ofertas, PUT /me/goal, export):
 * un objetivo propio de otro usuario no existe para él.
 */
@Component
public class GoalCatalog {

    private final CareerGoalRepository baseGoals;
    private final CustomGoalRepository customGoals;

    public GoalCatalog(CareerGoalRepository baseGoals, CustomGoalRepository customGoals) {
        this.baseGoals = baseGoals;
        this.customGoals = customGoals;
    }

    /** Los base en su orden y, después, los propios del usuario del más antiguo al más nuevo. */
    public List<CareerGoal> findAll(Long userId) {
        List<CareerGoal> all = new ArrayList<>(baseGoals.findAll());
        if (userId != null) {
            customGoals.findByUserId(userId).stream().map(CustomGoal::toCareerGoal).forEach(all::add);
        }
        return all;
    }

    public Optional<CareerGoal> find(Long userId, Long goalId) {
        if (goalId == null) {
            return Optional.empty();
        }
        Optional<CareerGoal> base = baseGoals.findById(goalId);
        if (base.isPresent() || userId == null) {
            return base;
        }
        return customGoals.find(userId, goalId).map(CustomGoal::toCareerGoal);
    }

    public CareerGoal require(Long userId, Long goalId) {
        return find(userId, goalId).orElseThrow(() -> new CareerGoalNotFoundException(goalId));
    }
}
