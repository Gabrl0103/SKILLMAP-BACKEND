package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.CareerGoalService;
import com.skillmap.api.application.service.ReadinessHistoryService;
import com.skillmap.api.presentation.dto.GoalResponse;
import com.skillmap.api.presentation.dto.ReadinessPointResponse;
import com.skillmap.api.presentation.dto.ReadinessResponse;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import com.skillmap.api.presentation.mapper.GoalResponseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class CareerGoalController {

    private final CareerGoalService goalService;
    private final ReadinessHistoryService readinessHistory;

    public CareerGoalController(CareerGoalService goalService, ReadinessHistoryService readinessHistory) {
        this.goalService = goalService;
        this.readinessHistory = readinessHistory;
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
    public ReadinessResponse getReadiness(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                          @PathVariable Long id) {
        return GoalResponseMapper.toResponse(goalService.getReadiness(userId, id));
    }

    /** % de preparación por día del usuario en ese objetivo. days: de 1 a 365 (30 si no viene). */
    @GetMapping("/{id}/readiness/history")
    public List<ReadinessPointResponse> getReadinessHistory(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                            @PathVariable Long id,
                                                            @RequestParam(defaultValue = "" + ReadinessHistoryService.DEFAULT_DAYS) int days) {
        return readinessHistory.getHistory(userId, id, days).stream()
                .map(s -> new ReadinessPointResponse(s.capturedOn(), s.percentage()))
                .toList();
    }
}
