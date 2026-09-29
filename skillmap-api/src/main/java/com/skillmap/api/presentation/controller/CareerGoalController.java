package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.CareerGoalService;
import com.skillmap.api.presentation.dto.GoalResponse;
import com.skillmap.api.presentation.dto.ReadinessResponse;
import com.skillmap.api.presentation.mapper.GoalResponseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class CareerGoalController {

    private final CareerGoalService goalService;

    public CareerGoalController(CareerGoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public List<GoalResponse> getAllGoals() {
        return goalService.getAllGoals().stream().map(GoalResponseMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public GoalResponse getGoalById(@PathVariable Long id) {
        return GoalResponseMapper.toResponse(goalService.getGoalById(id));
    }

    @GetMapping("/{id}/readiness")
    public ReadinessResponse getReadiness(@PathVariable Long id) {
        return GoalResponseMapper.toResponse(goalService.getReadiness(id));
    }
}
