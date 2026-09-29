package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.CareerGoal;

import java.util.List;
import java.util.Optional;

/** Puerto de salida para objetivos laborales. */
public interface CareerGoalRepository {
    List<CareerGoal> findAll();
    Optional<CareerGoal> findById(Long id);
    CareerGoal save(CareerGoal goal);
}
