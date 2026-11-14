package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalReadiness;

import java.util.List;

/**
 * Objetivos que ve un usuario: los base (de todos) y los propios suyos (CustomGoalService), con las mismas reglas.
 * Un objetivo propio de otro usuario no existe para él (404).
 */
public interface CareerGoalService {

    /** Solo los objetivos base. */
    List<CareerGoal> getAllGoals();

    /** Un objetivo base. */
    CareerGoal getGoalById(Long id);

    /** Los base y, después, los propios del usuario. */
    default List<CareerGoal> getGoals(Long userId) {
        return getAllGoals();
    }

    /** Un objetivo base o uno propio del usuario. */
    default CareerGoal getGoal(Long userId, Long id) {
        return getGoalById(id);
    }

    GoalReadiness getReadiness(Long userId, Long goalId);
}
