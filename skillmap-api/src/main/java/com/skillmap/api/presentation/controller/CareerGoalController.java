package com.skillmap.api.presentation.controller;

import jakarta.validation.Valid;
import com.skillmap.api.application.service.CareerGoalService;
import com.skillmap.api.application.service.CustomGoalService;
import com.skillmap.api.application.service.ReadinessHistoryService;
import com.skillmap.api.application.service.SkillSuggestionService;
import com.skillmap.api.presentation.dto.CustomGoalDeletedResponse;
import com.skillmap.api.presentation.dto.CustomGoalRequest;
import com.skillmap.api.presentation.dto.GoalResponse;
import com.skillmap.api.presentation.dto.ReadinessPointResponse;
import com.skillmap.api.presentation.dto.ReadinessResponse;
import com.skillmap.api.presentation.dto.SkillSuggestionRequest;
import com.skillmap.api.presentation.dto.SkillSuggestionResponse;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import com.skillmap.api.presentation.mapper.GoalResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Objetivos: los base (de todos) y los propios del usuario ("Otro objetivo", /api/goals/custom). Todos los
 * {id} aceptan los dos; el propio de otro usuario responde 404. Elegir uno: PUT /api/auth/me/goal.
 */
@RestController
@RequestMapping("/api/goals")
public class CareerGoalController {

    private final CareerGoalService goalService;
    private final CustomGoalService customGoals;
    private final ReadinessHistoryService readinessHistory;
    private final SkillSuggestionService suggestions;

    public CareerGoalController(CareerGoalService goalService, CustomGoalService customGoals,
                                ReadinessHistoryService readinessHistory, SkillSuggestionService suggestions) {
        this.goalService = goalService;
        this.customGoals = customGoals;
        this.readinessHistory = readinessHistory;
        this.suggestions = suggestions;
    }

    /** Los base y, después, los propios del usuario (custom: true, con sus fechas). */
    @GetMapping
    public List<GoalResponse> getAllGoals(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId) {
        List<GoalResponse> goals = new ArrayList<>(goalService.getAllGoals().stream()
                .map(GoalResponseMapper::toResponse).toList());
        customGoals.list(userId).stream().map(GoalResponseMapper::toResponse).forEach(goals::add);
        return goals;
    }

    @GetMapping("/{id}")
    public GoalResponse getGoalById(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId, @PathVariable Long id) {
        return GoalResponseMapper.toResponse(goalService.getGoal(userId, id));
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

    // --- Objetivos propios -------------------------------------------------------------------

    /** Los objetivos propios del usuario, del más antiguo al más nuevo. */
    @GetMapping("/custom")
    public List<GoalResponse> listCustom(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId) {
        return customGoals.list(userId).stream().map(GoalResponseMapper::toResponse).toList();
    }

    /** 201; 400 si no cumple las reglas; 409 si el nombre ya existe o ya tiene 3. */
    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse createCustom(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                     @Valid @RequestBody CustomGoalRequest request) {
        return GoalResponseMapper.toResponse(
                customGoals.create(userId, request.name(), request.description(), request.skillIds(),
                        request.newSkillsOrEmpty()));
    }

    /** 403 si es un objetivo base; 404 si no es suyo; 400 / 409 como al crear. */
    @PutMapping("/custom/{id}")
    public GoalResponse updateCustom(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId, @PathVariable Long id,
                                     @Valid @RequestBody CustomGoalRequest request) {
        return GoalResponseMapper.toResponse(
                customGoals.update(userId, id, request.name(), request.description(), request.skillIds(),
                        request.newSkillsOrEmpty()));
    }

    /**
     * Habilidades sugeridas para el nombre de un rol (diccionario; si no coincide, la IA del Analizador como respaldo).
     * No guarda nada. 400 si el nombre no es válido; sin sugerencias: 200 con source "none" y la lista vacía.
     */
    @PostMapping("/custom/suggestions")
    public SkillSuggestionResponse suggestSkills(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                 @Valid @RequestBody SkillSuggestionRequest request) {
        return SkillSuggestionResponse.from(suggestions.suggest(userId, request.role()));
    }

    /** Borra el objetivo y su historial. Responde el objetivo elegido después (cambia si se borró el elegido). */
    @DeleteMapping("/custom/{id}")
    public CustomGoalDeletedResponse deleteCustom(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                  @PathVariable Long id) {
        return new CustomGoalDeletedResponse(customGoals.delete(userId, id));
    }
}
