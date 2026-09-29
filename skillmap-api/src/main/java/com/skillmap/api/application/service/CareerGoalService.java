package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalReadiness;

import java.util.List;

public interface CareerGoalService {
    List<CareerGoal> getAllGoals();
    CareerGoal getGoalById(Long id);
    GoalReadiness getReadiness(Long goalId);
}
